package com.openclaw.lanes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-lane mutable state held inside {@link LaneDispatcher}.
 * <p>
 * Mirrors TS {@code LaneState} from {@code src/process/command-queue.ts}. <b>Six fields exactly</b>:
 * <ul>
 *   <li>{@code queue} — FIFO waiting queue of {@link QueueEntry}</li>
 *   <li>{@code activeTaskIds} — task ids currently executing in this lane</li>
 *   <li>{@code maxConcurrent} — lane capacity (&gt;=1)</li>
 *   <li>{@code generation} — bumped by {@code resetAllLanes(reason)}; inflight tasks must check {@code ==} on resolve</li>
 *   <li>{@code draining} — reentry guard for {@code drainLane}; must be set back to {@code false} on exception</li>
 *   <li>{@code lane} — the lane name this state belongs to (redundant pointer for diagnostics)</li>
 * </ul>
 *
 * <p>Instances are always accessed under {@code LaneDispatcher#lock}. Never publish references.
 */
final class LaneState {

    final String lane;
    final Deque<QueueEntry<?>> queue = new ArrayDeque<>();
    final Set<Long> activeTaskIds = new LinkedHashSet<>();
    int maxConcurrent;
    long generation;
    boolean draining;

    LaneState(final String lane, final int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be >= 1, got " + maxConcurrent);
        }
        this.lane = lane;
        this.maxConcurrent = maxConcurrent;
        this.generation = 0L;
        this.draining = false;
    }

    boolean isIdle() {
        return queue.isEmpty() && activeTaskIds.isEmpty();
    }
}
