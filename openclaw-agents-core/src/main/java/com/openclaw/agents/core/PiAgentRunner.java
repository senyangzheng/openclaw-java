package com.openclaw.agents.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.openclaw.lanes.EnqueueOptions;
import com.openclaw.lanes.SessionLaneCoordinator;
import com.openclaw.logging.MdcKeys;
import com.openclaw.logging.MdcScope;
import com.openclaw.stream.AgentEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Top-level agent runtime entry point. Mirrors openclaw-ts {@code PiEmbeddedRunner.run()} from
 * {@code src/agents/pi-embedded-runner/run.ts}.
 *
 * <p>Responsibilities (M3.1 scope):
 * <ol>
 *   <li>Guard the session with {@link ActiveRunRegistry#tryRegister(AgentRunHandle)} (reject duplicate runs with
 *       {@link AgentErrorCode#ACTIVE_RUN_CONFLICT})</li>
 *   <li>Schedule the actual work through {@link SessionLaneCoordinator#run(String, com.openclaw.lanes.CommandLane,
 *       java.util.function.Supplier, EnqueueOptions, EnqueueOptions)} so session-level serialization and global-lane
 *       capacity caps are honoured</li>
 *   <li>Drive the state-machine transitions {@link AgentRunState#IDLE} →
 *       {@link AgentRunState#QUEUED_SESSION} → {@link AgentRunState#QUEUED_GLOBAL} and delegate
 *       {@link AgentRunState#ATTEMPTING} onwards to {@link AttemptExecutor}</li>
 *   <li>Publish events through a per-run {@link SubscribeState} and clear the active-run slot at termination
 *       (using {@link ActiveRunRegistry#clearIfMatches(com.openclaw.sessions.SessionKey, AgentRunHandle)} to
 *       prevent stale clears)</li>
 * </ol>
 *
 * <p>Retry, compaction, tool pipeline and model fallback are <b>out of scope</b> for M3.1; they will be layered
 * on top by later milestones (M3.2–M3.5) as separate cross-cutting components composed around
 * {@link AttemptExecutor}.
 */
public final class PiAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(PiAgentRunner.class);

    private final SessionLaneCoordinator lanes;
    private final ActiveRunRegistry activeRuns;
    private final AttemptExecutor attemptExecutor;

    public PiAgentRunner(final SessionLaneCoordinator lanes,
                         final ActiveRunRegistry activeRuns,
                         final AttemptExecutor attemptExecutor) {
        this.lanes = Objects.requireNonNull(lanes, "lanes");
        this.activeRuns = Objects.requireNonNull(activeRuns, "activeRuns");
        this.attemptExecutor = Objects.requireNonNull(attemptExecutor, "attemptExecutor");
    }

    /**
     * Submit an agent run. The returned {@code Flux} emits {@link AgentEvent}s in order and completes when the
     * attempt terminates (either normally with {@link AgentEvent.Done} or with an
     * {@link AgentEvent.Error}). If the session already has an active run, the {@code Flux} signals
     * {@link AgentErrorCode#ACTIVE_RUN_CONFLICT} on subscribe.
     */
    public AgentRunOutcome submit(final AgentRunRequest request) {
        Objects.requireNonNull(request, "request");

        final AgentRunHandle handle = AgentRunHandle.create(request.sessionKey());
        final SubscribeState subscribe = new SubscribeState();

        // Hot-bridge: subscribers to the returned Flux receive every event emitted into SubscribeState, even
        // if they subscribe after the attempt has already started. Uses a replay(all) sink so no events are
        // lost between registration and the first downstream subscribe.
        final Sinks.Many<AgentEvent> bridge = Sinks.many().replay().all();
        subscribe.peek(ev -> bridge.tryEmitNext(ev));

        if (!activeRuns.tryRegister(handle)) {
            final com.openclaw.common.error.OpenClawException conflict =
                    new com.openclaw.common.error.OpenClawException(
                            AgentErrorCode.ACTIVE_RUN_CONFLICT,
                            "Session already has an active run: " + request.sessionKey().asString());
            subscribe.emit(new AgentEvent.Error(AgentErrorCode.ACTIVE_RUN_CONFLICT.code(), conflict.getMessage()));
            subscribe.error(conflict);
            bridge.tryEmitError(conflict);
            return new AgentRunOutcome(handle, subscribe, bridge.asFlux());
        }

        handle.advance(AgentRunState.QUEUED_SESSION);

        final AtomicReference<Throwable> failure = new AtomicReference<>();

        lanes.run(
                request.sessionKey().asString(),
                request.globalLane(),
                () -> {
                    // We are now inside the session+global lanes. Transition to QUEUED_GLOBAL then let the
                    // attempt executor take over (it will advance to ATTEMPTING / STREAMING / terminal states).
                    try {
                        handle.advance(AgentRunState.QUEUED_GLOBAL);
                    } catch (RuntimeException ignored) {
                        // a concurrent abort may have flipped us straight to ABORTING; not fatal
                    }
                    try (var ignored = MdcScope.of(MdcKeys.SESSION_ID, request.sessionKey().asString())) {
                        attemptExecutor.execute(request, handle, subscribe).blockLast();
                        return null;
                    }
                },
                EnqueueOptions.DEFAULTS,
                EnqueueOptions.DEFAULTS)
                .whenComplete((ignored, err) -> {
                    if (err != null) {
                        failure.set(err);
                        log.warn("agent.run.lane.error handle={} err={}", handle.id(), err.toString());
                        if (!subscribe.isTerminated()) {
                            subscribe.emit(new AgentEvent.Error("E_LANE", err.toString()));
                            subscribe.error(err);
                            try {
                                handle.advance(AgentRunState.FAILED);
                            } catch (RuntimeException ignore) {
                                // state may already be terminal
                            }
                        }
                    }
                    if (!subscribe.isTerminated()) {
                        // Lane completed but nothing terminated the subscribe — defensive completion.
                        subscribe.emit(AgentEvent.Done.STOP);
                        subscribe.complete();
                    }
                    bridge.tryEmitComplete();
                    final boolean cleared = activeRuns.clearIfMatches(handle.sessionKey(), handle);
                    log.debug("agent.run.end handle={} state={} cleared={}",
                            handle.id(), handle.currentState(), cleared);
                });

        return new AgentRunOutcome(handle, subscribe, bridge.asFlux());
    }

    /**
     * Request cancellation of a run. Sets the abort flag on the handle; the next state transition in the
     * attempt will propagate it (open-loop cancellation — reactive cleanup lives in future milestones).
     */
    public boolean abort(final AgentRunHandle handle, final String reason) {
        Objects.requireNonNull(handle, "handle");
        handle.abort(reason);
        log.info("agent.run.abort handle={} reason={}", handle.id(), reason);
        return true;
    }

    public ActiveRunRegistry activeRuns() {
        return activeRuns;
    }

    /**
     * Submit result envelope. Callers typically subscribe to {@link #events()} for token streaming and keep
     * {@link #handle()} around for {@link #abort(AgentRunHandle, String)}.
     */
    public record AgentRunOutcome(AgentRunHandle handle,
                                  SubscribeState subscribe,
                                  Flux<AgentEvent> events) {
        public Flux<AgentEvent> events() {
            return events;
        }
    }
}
