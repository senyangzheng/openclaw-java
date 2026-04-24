package com.openclaw.lanes;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Internal queue entry for {@link LaneDispatcher}. Mirrors the TS {@code QueueEntry} struct from
 * {@code src/process/command-queue.ts}.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id} — monotonically-increasing task id used by {@code activeTaskIds}</li>
 *   <li>{@code task} — the user-supplied unit of work</li>
 *   <li>{@code result} — promise resolved/rejected when the task finishes</li>
 *   <li>{@code enqueuedAt} — system millis at insertion (used to compute {@code waitedMs})</li>
 *   <li>{@code options} — {@link EnqueueOptions} including {@code warnAfterMs} (default 2000) and {@code onWait}</li>
 *   <li>{@code warnFired} — flips to {@code true} the first time the wait warning fires (idempotent)</li>
 * </ul>
 *
 * <p>Package-private; callers interact via {@link LaneDispatcher#enqueue}.
 */
final class QueueEntry<T> {

    final long id;
    final Supplier<? extends T> task;
    final CompletableFuture<T> result;
    final long enqueuedAt;
    final EnqueueOptions options;
    boolean warnFired;

    QueueEntry(final long id,
               final Supplier<? extends T> task,
               final CompletableFuture<T> result,
               final long enqueuedAt,
               final EnqueueOptions options) {
        this.id = id;
        this.task = task;
        this.result = result;
        this.enqueuedAt = enqueuedAt;
        this.options = options == null ? EnqueueOptions.DEFAULTS : options;
        this.warnFired = false;
    }
}
