package com.openclaw.lanes;

/**
 * Public read-only snapshot of a {@link LaneState}.
 * Useful for metrics / diagnostics / assertions in tests.
 */
public record LaneSnapshot(String lane,
                           int queueDepth,
                           int active,
                           int maxConcurrent,
                           long generation,
                           boolean draining) {
}
