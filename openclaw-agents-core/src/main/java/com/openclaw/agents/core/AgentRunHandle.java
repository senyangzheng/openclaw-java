package com.openclaw.agents.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.sessions.SessionKey;

/**
 * Opaque, stable handle to a single agent run. Mirrors openclaw-ts {@code RunHandle} from
 * {@code src/agents/pi-embedded-runner/run.ts}.
 *
 * <p>Thread-safe: state and abort-flag use atomic refs; callers mutate state via {@link #advance(AgentRunState)}.
 */
public final class AgentRunHandle {

    private final String id;
    private final SessionKey sessionKey;
    private final Instant startedAt;
    private final AtomicReference<AgentRunState> state;
    private final AtomicBoolean aborted;
    private final AtomicReference<String> abortReason;

    private AgentRunHandle(final String id, final SessionKey sessionKey) {
        this.id = Objects.requireNonNull(id, "id");
        this.sessionKey = Objects.requireNonNull(sessionKey, "sessionKey");
        this.startedAt = Instant.now();
        this.state = new AtomicReference<>(AgentRunState.IDLE);
        this.aborted = new AtomicBoolean(false);
        this.abortReason = new AtomicReference<>(null);
    }

    public static AgentRunHandle create(final SessionKey sessionKey) {
        return new AgentRunHandle(UUID.randomUUID().toString(), sessionKey);
    }

    public String id() {
        return id;
    }

    public SessionKey sessionKey() {
        return sessionKey;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public AgentRunState currentState() {
        return state.get();
    }

    /**
     * Attempt to atomically transition to {@code next}. Throws
     * {@link AgentErrorCode#INVALID_TRANSITION} when the transition is not allowed from the current state.
     *
     * <p>Uses {@code AtomicReference#compareAndSet}, so concurrent callers either both win (impossible) or one
     * wins and the other throws — callers must be prepared for that.
     */
    public AgentRunState advance(final AgentRunState next) {
        Objects.requireNonNull(next, "next");
        while (true) {
            final AgentRunState current = state.get();
            if (!current.canTransitionTo(next)) {
                throw new OpenClawException(AgentErrorCode.INVALID_TRANSITION,
                        "Illegal transition: " + current + " → " + next + " (handle=" + id + ")");
            }
            if (state.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /**
     * Mark this run as aborted. Idempotent. The next attempt to
     * {@link #advance(AgentRunState)} while already aborted will still succeed
     * (state machine transitions are independent of the abort flag — transitions
     * include ABORTING as a valid successor from most states).
     */
    public void abort(final String reason) {
        aborted.set(true);
        abortReason.compareAndSet(null, reason);
    }

    public boolean isAborted() {
        return aborted.get();
    }

    public String abortReason() {
        return abortReason.get();
    }

    @Override
    public String toString() {
        return "AgentRunHandle{" +
                "id=" + id +
                ", session=" + sessionKey.asString() +
                ", state=" + state.get() +
                ", aborted=" + aborted.get() +
                '}';
    }
}
