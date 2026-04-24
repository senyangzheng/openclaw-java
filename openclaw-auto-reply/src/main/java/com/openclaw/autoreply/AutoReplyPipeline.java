package com.openclaw.autoreply;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.openclaw.agents.core.AgentRunRequest;
import com.openclaw.agents.core.PiAgentRunner;
import com.openclaw.channels.core.InboundMessage;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.logging.MdcKeys;
import com.openclaw.logging.MdcScope;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.sessions.Session;
import com.openclaw.sessions.SessionKey;
import com.openclaw.sessions.SessionRepository;
import com.openclaw.stream.AgentEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Thin channel-edge orchestrator. After M3/A1 this class is a <b>4-layer thin adapter</b>:
 * <pre>
 *   inbound transform  →  session append(user)  →  PiAgentRunner.submit(...)  →  session append(assistant)  →  outbound transform
 * </pre>
 *
 * <p>All agent logic (before/after hooks, provider failover, lane scheduling, active-run registry) lives
 * below the {@link PiAgentRunner} boundary. This class only handles the channel-side I/O:
 * <ul>
 *   <li><b>Session persistence</b>: load / append user / append assistant / save (the only I/O side-effects here)</li>
 *   <li><b>Inbound → AgentRunRequest</b> packaging</li>
 *   <li><b>AgentEvent → ChatChunkEvent / OutboundMessage</b> reverse-translation for the web / gateway channels</li>
 * </ul>
 *
 * <p><b>Removed responsibilities</b> (now owned elsewhere):
 * <ul>
 *   <li>{@code before_agent_start} hook — moved into {@code AttemptExecutor}</li>
 *   <li>Provider call + failover — moved into {@code ProviderDispatcher}</li>
 *   <li>Session-level serialization — moved into {@code SessionLaneCoordinator} (invoked by {@link PiAgentRunner})</li>
 *   <li>Active-run guard — moved into {@code ActiveRunRegistry}</li>
 * </ul>
 */
public class AutoReplyPipeline {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyPipeline.class);

    private final SessionRepository sessions;
    private final PiAgentRunner agentRunner;

    public AutoReplyPipeline(final SessionRepository sessions, final PiAgentRunner agentRunner) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.agentRunner = Objects.requireNonNull(agentRunner, "agentRunner");
    }

    // =====================================================================================================
    // Blocking path
    // =====================================================================================================

    public OutboundMessage handle(final InboundMessage inbound) {
        Objects.requireNonNull(inbound, "inbound");
        final SessionKey sessionKey = inbound.routingKey().toSessionKey();

        try (var ignored = MdcScope.of(MdcKeys.CHANNEL, inbound.routingKey().account().channelId())
                .with(MdcKeys.SESSION_ID, sessionKey.asString())
                .with(MdcKeys.PROVIDER, "agent-runner")) {

            log.info("auto-reply.inbound messageId={} textLen={}",
                    inbound.messageId(), inbound.text().length());

            final Session session = sessions.loadOrCreate(sessionKey);
            final ChatMessage userMsg = ChatMessage.user(inbound.text());
            session.append(userMsg);
            final List<ChatMessage> history = List.copyOf(session.messages());

            final AgentRunRequest request = new AgentRunRequest(
                    sessionKey, null, userMsg, history,
                    Map.of("messageId", inbound.messageId(),
                            "channelId", inbound.routingKey().account().channelId()));

            final PiAgentRunner.AgentRunOutcome outcome = agentRunner.submit(request);

            final StringBuilder assistant = new StringBuilder();
            final AtomicReference<AgentEvent.Error> errorRef = new AtomicReference<>();
            try {
                outcome.events()
                        .doOnNext(ev -> {
                            if (ev instanceof AgentEvent.Delta d) {
                                assistant.append(d.content());
                            } else if (ev instanceof AgentEvent.Error err) {
                                errorRef.set(err);
                            }
                        })
                        .blockLast();
            } catch (RuntimeException ex) {
                // Translator / lane failures surface here. Treat the same as an AgentEvent.Error —
                // the error event has already been emitted onto the bridge in that case.
                if (errorRef.get() == null) {
                    errorRef.set(new AgentEvent.Error("E_RUNNER", ex.getMessage() == null ? ex.toString() : ex.getMessage()));
                }
            }

            final String replyText = decideReply(assistant, errorRef.get());
            session.append(ChatMessage.assistant(replyText));
            sessions.save(session);

            if (errorRef.get() != null) {
                log.info("auto-reply.outbound.error code={} replyLen={}",
                        errorRef.get().code(), replyText.length());
            } else {
                log.info("auto-reply.outbound state={} replyLen={}",
                        outcome.handle().currentState(), replyText.length());
            }
            return OutboundMessage.replyTo(inbound, replyText);
        }
    }

    // =====================================================================================================
    // Streaming path
    // =====================================================================================================

    /**
     * Streaming counterpart of {@link #handle(InboundMessage)}. Emits {@link ChatChunkEvent} so the Web SSE
     * endpoint stays backward-compatible (M3.1 will introduce a canonical {@code AgentEvent} SSE variant —
     * this channel-adapter keeps the legacy wire format for now).
     *
     * <p>Session persistence uses the accumulated deltas — if the agent stream fails midway, whatever was
     * produced up to that point is still persisted so the conversation stays readable.
     */
    public Flux<ChatChunkEvent> streamHandle(final InboundMessage inbound) {
        Objects.requireNonNull(inbound, "inbound");
        final SessionKey sessionKey = inbound.routingKey().toSessionKey();
        final String channelId = inbound.routingKey().account().channelId();

        final Session session = sessions.loadOrCreate(sessionKey);
        final ChatMessage userMsg = ChatMessage.user(inbound.text());
        session.append(userMsg);
        final List<ChatMessage> history = List.copyOf(session.messages());

        final AgentRunRequest request = new AgentRunRequest(
                sessionKey, null, userMsg, history,
                Map.of("messageId", inbound.messageId(),
                        "channelId", channelId));

        final StringBuilder assistant = new StringBuilder();

        return Flux.defer(() -> {
                    try (var ignored = MdcScope.of(MdcKeys.CHANNEL, channelId)
                            .with(MdcKeys.SESSION_ID, sessionKey.asString())
                            .with(MdcKeys.PROVIDER, "agent-runner")) {
                        log.info("auto-reply.stream.begin messageId={} textLen={}",
                                inbound.messageId(), inbound.text().length());
                        return agentRunner.submit(request).events();
                    }
                })
                .mapNotNull(ev -> translate(ev, assistant))
                .concatWith(Flux.defer(() -> {
                    try (var ignored = MdcScope.of(MdcKeys.CHANNEL, channelId)
                            .with(MdcKeys.SESSION_ID, sessionKey.asString())
                            .with(MdcKeys.PROVIDER, "agent-runner")) {
                        final String reply = assistant.length() == 0 ? "" : assistant.toString();
                        if (!reply.isEmpty()) {
                            session.append(ChatMessage.assistant(reply));
                            sessions.save(session);
                        }
                        log.info("auto-reply.stream.end assistantLen={}", assistant.length());
                    }
                    return Flux.empty();
                }))
                .doOnError(err -> log.warn("auto-reply.stream.error sessionId={}",
                        sessionKey.asString(), err));
    }

    // =====================================================================================================
    // Internal
    // =====================================================================================================

    private static String decideReply(final StringBuilder assistant, final AgentEvent.Error error) {
        final String text = assistant.toString();
        if (error != null) {
            if (!text.isEmpty()) {
                return text;
            }
            if ("E_HOOK_BLOCKED".equals(error.code())) {
                return "[blocked] " + error.message();
            }
            return "[error:" + error.code() + "] " + error.message();
        }
        return text;
    }

    /** Reverse-translate {@link AgentEvent} to {@link ChatChunkEvent}. Returns {@code null} to drop. */
    private static ChatChunkEvent translate(final AgentEvent ev, final StringBuilder buffer) {
        if (ev instanceof AgentEvent.Delta d) {
            buffer.append(d.content());
            return new ChatChunkEvent.Delta(d.content());
        }
        if (ev instanceof AgentEvent.Done done) {
            return new ChatChunkEvent.Done(done.reason(), done.usage());
        }
        if (ev instanceof AgentEvent.Error err) {
            return new ChatChunkEvent.Error(err.code(), err.message());
        }
        // Reasoning / ToolCall / ToolResult do not flow out over legacy SSE in M3.1 — they land on the
        // AgentEvent-flavoured SSE variant scheduled for M3.1 proper.
        return null;
    }
}
