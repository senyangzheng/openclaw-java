package com.openclaw.agents.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.openclaw.agents.core.hooks.BeforeAgentStartEvent;
import com.openclaw.agents.core.hooks.BeforeAgentStartMerge;
import com.openclaw.agents.core.hooks.RunAgentEndEvent;
import com.openclaw.hooks.HookBlockedException;
import com.openclaw.hooks.HookContext;
import com.openclaw.hooks.HookNames;
import com.openclaw.hooks.HookRunner;
import com.openclaw.hooks.ModifyingHookResult;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.registry.ProviderDispatcher;
import com.openclaw.stream.AgentEvent;
import com.openclaw.stream.ChatChunkEventTranslator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Single-attempt executor. Owns the linear happy-path inside one provider call:
 * <ol>
 *   <li>Invoke {@link HookNames#BEFORE_AGENT_START} (modifying hook); honour
 *       {@link com.openclaw.hooks.HookOutcome.ShortCircuit} (skip provider entirely) and
 *       {@link com.openclaw.hooks.HookOutcome.Block} (propagate {@link HookBlockedException})</li>
 *   <li>Build a {@link ChatRequest} from history + prepended context delta</li>
 *   <li>Call {@link ProviderDispatcher#streamChat(ChatRequest)} (failover + cooldown + structured attempts
 *       happens inside the dispatcher — this executor sees a single cold {@link Flux}) and translate the
 *       resulting chunks to {@link AgentEvent} via {@link ChatChunkEventTranslator}</li>
 *   <li>Forward every event to {@link SubscribeState#emit(AgentEvent)}</li>
 *   <li>On terminal event / flux completion, invoke {@link HookNames#RUN_AGENT_END} (void hook)</li>
 * </ol>
 *
 * <p><b>Out of scope (deferred to M3.2+)</b>: context-window guard, history sanitizer, tool-policy pipeline,
 * compaction chain, model-fallback — this class is the skeleton that future milestones wrap / extend.
 *
 * <p>Thread-safety: stateless aside from dependencies; safe to share across runs.
 */
public final class AttemptExecutor {

    private static final Logger log = LoggerFactory.getLogger(AttemptExecutor.class);

    private final ProviderDispatcher dispatcher;
    private final HookRunner hookRunner;

    public AttemptExecutor(final ProviderDispatcher dispatcher, final HookRunner hookRunner) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.hookRunner = Objects.requireNonNull(hookRunner, "hookRunner");
    }

    /**
     * Execute one attempt. Returns a cold {@link Flux} of {@link AgentEvent}s. Subscribing triggers the actual
     * provider call; state transitions on the {@link AgentRunHandle} happen eagerly at subscribe time.
     *
     * @param request   request envelope (history + user message)
     * @param handle    current run handle (state machine + abort flag)
     * @param subscribe per-run event aggregator
     */
    public Flux<AgentEvent> execute(final AgentRunRequest request,
                                    final AgentRunHandle handle,
                                    final SubscribeState subscribe) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(subscribe, "subscribe");

        return Flux.defer(() -> {
            handle.advance(AgentRunState.ATTEMPTING);

            // -------- before_agent_start hook --------
            final BeforeAgentStartEvent evt = new BeforeAgentStartEvent(
                    request.sessionKey(),
                    request.userMessage(),
                    request.history(),
                    request.metadata());
            final ModifyingHookResult<BeforeAgentStartMerge> hookResult;
            try {
                hookResult = hookRunner.runModifyingHook(
                        HookNames.BEFORE_AGENT_START,
                        evt,
                        HookContext.of(HookNames.BEFORE_AGENT_START, request.metadata()),
                        BeforeAgentStartMerge.empty(),
                        BeforeAgentStartMerge::merge);
            } catch (HookBlockedException ex) {
                subscribe.emit(new AgentEvent.Error("E_HOOK_BLOCKED", ex.blockReason()));
                handle.advance(AgentRunState.FAILED);
                subscribe.error(ex);
                return Flux.error(ex);
            }

            // Short-circuit: HelloPlugin-style `/hello` user commands. Emit a synthetic Delta+Done and skip
            // the provider entirely.
            if (hookResult.isShortCircuit()) {
                final String reply = hookResult.shortCircuit();
                final String replySafe = reply == null ? "" : reply;
                subscribe.emit(new AgentEvent.Delta(replySafe));
                subscribe.emit(AgentEvent.Done.STOP);
                handle.advance(AgentRunState.COMPLETED);
                subscribe.complete();
                runEndHookAsync(handle, "short-circuit", null);
                return Flux.just(
                        (AgentEvent) new AgentEvent.Delta(replySafe),
                        AgentEvent.Done.STOP);
            }

            // -------- build provider request --------
            final BeforeAgentStartMerge acc = hookResult.accumulator();
            final List<ChatMessage> messages = acc.buildEffectiveMessages(request.history(), request.userMessage());
            final ChatRequest chatReq = new ChatRequest(acc.modelOverride(), messages, acc.providerExtras());

            log.debug("agent.attempt.begin handle={} session={} messages={} promptOverride={}",
                    handle.id(), request.sessionKey().asString(),
                    messages.size(), acc.systemPrompt() != null);

            // -------- call provider + translate --------
            final Flux<ChatChunkEvent> providerFlux = dispatcher.streamChat(chatReq);
            final Flux<AgentEvent> translated = ChatChunkEventTranslator.translateFlux(providerFlux);

            handle.advance(AgentRunState.STREAMING);

            return translated
                    .doOnNext(subscribe::emit)
                    .doOnComplete(() -> {
                        // If provider didn't emit a terminal event, synthesize one.
                        if (!subscribe.accumulator().isTerminated()) {
                            subscribe.emit(AgentEvent.Done.STOP);
                        }
                        if (handle.currentState() == AgentRunState.STREAMING) {
                            handle.advance(AgentRunState.COMPLETED);
                        }
                        subscribe.complete();
                        runEndHookAsync(handle, "ok", null);
                    })
                    .doOnError(err -> {
                        if (!subscribe.accumulator().isTerminated()) {
                            subscribe.emit(new AgentEvent.Error("E_PROVIDER", err.getMessage() == null
                                    ? err.toString()
                                    : err.getMessage()));
                        }
                        try {
                            handle.advance(AgentRunState.FAILED);
                        } catch (RuntimeException ignored) {
                            // already transitioned
                        }
                        subscribe.error(err);
                        runEndHookAsync(handle, "error", err);
                    });
        });
    }

    private void runEndHookAsync(final AgentRunHandle handle, final String status, final Throwable error) {
        final Map<String, Object> meta = new HashMap<>();
        meta.put("status", status);
        if (error != null) {
            meta.put("error", error.toString());
        }
        final RunAgentEndEvent evt = new RunAgentEndEvent(handle.id(), handle.sessionKey(), status, error);
        hookRunner.runVoidHook(HookNames.RUN_AGENT_END, evt, HookContext.of(HookNames.RUN_AGENT_END, meta));
    }

}
