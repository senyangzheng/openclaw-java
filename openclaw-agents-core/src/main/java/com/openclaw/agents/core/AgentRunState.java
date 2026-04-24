package com.openclaw.agents.core;

import java.util.EnumSet;
import java.util.Set;

/**
 * State machine governing a single agent run.
 *
 * <p>Mirrors openclaw-ts {@code run / attempt / subscribe} state transitions distilled from:
 * <ul>
 *   <li>{@code src/agents/pi-embedded-runner/run.ts}</li>
 *   <li>{@code src/agents/attempt/executor.ts}</li>
 *   <li>{@code src/process/command-queue.ts}</li>
 * </ul>
 *
 * <p><b>Canonical happy path</b>:
 * <pre>
 *   IDLE → QUEUED_SESSION → QUEUED_GLOBAL → ATTEMPTING → STREAMING
 *        → [COMPACTING (optional)] → COMPLETED → IDLE
 * </pre>
 *
 * <p><b>Control states</b> (can overlap with execution states):
 * <ul>
 *   <li>{@link #ABORTING} — user requested cancel; terminal state follows when the attempt unwinds</li>
 *   <li>{@link #WAITING_COMPACTION_RETRY} — compaction retry pending; see M3.2</li>
 * </ul>
 *
 * <p>See {@code .cursor/plan/04-milestones.md} M3.1 state-machine diagram and
 * {@code .cursor/plan/05-translation-conventions.md} §12 #2.
 */
public enum AgentRunState {

    /** No run scheduled. */
    IDLE,

    /** Enqueued on the session (outer) lane; waiting for the session slot. */
    QUEUED_SESSION,

    /** Enqueued on the global (inner) lane; waiting for the category cap. */
    QUEUED_GLOBAL,

    /** Attempt has been picked up; pre-flight (history sanitize, context guard) underway. */
    ATTEMPTING,

    /** Provider call emitted its first event; consuming {@code Flux<AgentEvent>}. */
    STREAMING,

    /** Optional: compacting conversation history due to context overflow. */
    COMPACTING,

    /** Waiting to retry a compaction cycle (see M3.2). */
    WAITING_COMPACTION_RETRY,

    /** Attempt finished successfully; final events flushed. */
    COMPLETED,

    /** Attempt finished with a non-recoverable error (already classified via FailoverReason). */
    FAILED,

    /** Abort signal observed; cleaning up inflight provider / tool calls. */
    ABORTING;

    /**
     * Valid successors for this state. Callers (runners / tests) MUST verify transitions against this set;
     * illegal transitions indicate a state-machine bug.
     */
    public Set<AgentRunState> allowedNext() {
        return switch (this) {
            case IDLE -> EnumSet.of(QUEUED_SESSION);
            case QUEUED_SESSION -> EnumSet.of(QUEUED_GLOBAL, ABORTING);
            case QUEUED_GLOBAL -> EnumSet.of(ATTEMPTING, ABORTING);
            case ATTEMPTING -> EnumSet.of(STREAMING, COMPLETED, FAILED, ABORTING, COMPACTING);
            case STREAMING -> EnumSet.of(COMPACTING, COMPLETED, FAILED, ABORTING);
            case COMPACTING -> EnumSet.of(WAITING_COMPACTION_RETRY, STREAMING, ATTEMPTING, COMPLETED, FAILED, ABORTING);
            case WAITING_COMPACTION_RETRY -> EnumSet.of(ATTEMPTING, FAILED, ABORTING);
            case COMPLETED, FAILED -> EnumSet.of(IDLE);
            case ABORTING -> EnumSet.of(FAILED, COMPLETED, IDLE);
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isActive() {
        return this == ATTEMPTING || this == STREAMING || this == COMPACTING;
    }

    public boolean canTransitionTo(final AgentRunState next) {
        if (next == null || next == this) {
            return false;
        }
        return allowedNext().contains(next);
    }
}
