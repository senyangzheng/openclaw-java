package com.openclaw.lanes;

import java.util.function.LongConsumer;

/**
 * Per-task options passed to {@code LaneDispatcher.enqueue(...)}.
 *
 * <p>Mirrors openclaw TS {@code QueueEntry.warnAfterMs / onWait} fields from
 * {@code src/process/command-queue.ts}. The default {@link #DEFAULT_WARN_AFTER_MS} of
 * {@value #DEFAULT_WARN_AFTER_MS} ms matches TS {@code opts?.warnAfterMs ?? 2_000}.
 *
 * <p><b>Important:</b> exceeding {@code warnAfterMs} only triggers {@code onWait}
 * and a {@code log.warn}; it never cancels the task. See
 * {@code .cursor/plan/05-translation-conventions.md} §14 #2.
 *
 * @param warnAfterMs threshold (ms) after which {@code onWait} is invoked while still queued
 * @param onWait      side-effect callback invoked once when {@code waitedMs &gt;= warnAfterMs}; nullable
 */
public record EnqueueOptions(long warnAfterMs, LongConsumer onWait) {

    /** Default queue-wait warning threshold. Matches openclaw TS default of 2 seconds. */
    public static final long DEFAULT_WARN_AFTER_MS = 2_000L;

    /** Default options: 2s warning threshold, no callback. */
    public static final EnqueueOptions DEFAULTS = new EnqueueOptions(DEFAULT_WARN_AFTER_MS, null);

    public EnqueueOptions {
        if (warnAfterMs < 0) {
            throw new IllegalArgumentException("warnAfterMs must be >= 0, got " + warnAfterMs);
        }
    }

    public static EnqueueOptions warnAfter(final long warnAfterMs) {
        return new EnqueueOptions(warnAfterMs, null);
    }

    public static EnqueueOptions of(final long warnAfterMs, final LongConsumer onWait) {
        return new EnqueueOptions(warnAfterMs, onWait);
    }
}
