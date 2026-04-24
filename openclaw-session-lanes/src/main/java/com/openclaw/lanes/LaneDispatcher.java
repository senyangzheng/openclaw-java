package com.openclaw.lanes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-layer command-lane dispatcher.
 *
 * <p>Mirrors {@code src/process/command-queue.ts} from openclaw-ts.
 *
 * <p><b>State machine invariants</b> (see {@code .cursor/plan/05-translation-conventions.md} §14):
 * <ol>
 *   <li>Tasks enter {@code queue}, drain into {@code activeTaskIds} respecting {@code maxConcurrent}.</li>
 *   <li>{@link #drainLane(String)} is guarded by {@code draining=true} (anti-reentry); the flag must always be
 *       reset in a {@code finally} block so failed tasks don't permanently freeze the lane.</li>
 *   <li>{@link #clearLane(String)} cancels only queued tasks (active tasks are untouched), rejecting them with
 *       {@link CommandLaneClearedException}.</li>
 *   <li>{@link #resetAllLanes(String)} bumps {@code generation} on every lane, clears every queue, and still
 *       lets inflight tasks finish naturally; they are filtered via {@code ==} check on {@code generation} so
 *       their completions don't mutate the post-reset state.</li>
 *   <li>{@link #waitForActiveTasks(long)} polls every {@value #POLL_INTERVAL_MS} ms.</li>
 *   <li>{@link #setMaxConcurrent(String, int)} takes effect immediately: if new capacity is larger, extra queued
 *       tasks drain right away; if smaller, inflight tasks finish first and only new pops respect the limit.</li>
 *   <li>Probe lanes (see {@link LaneNames#isProbeLane(String)}) silence error logs; the result future still
 *       completes exceptionally so callers may react.</li>
 * </ol>
 *
 * <p><b>Thread-safety:</b> all lane-state mutations happen under {@link #stateLock}. Tasks execute on
 * {@link #executor} (defaults to a virtual-thread-per-task executor) <i>outside</i> the lock.
 */
public final class LaneDispatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LaneDispatcher.class);

    /** Polling interval for {@link #waitForActiveTasks(long)}. Matches TS {@code POLL_INTERVAL_MS = 50}. */
    public static final long POLL_INTERVAL_MS = 50L;

    private final String name;
    private final int defaultMaxConcurrent;
    private final Executor executor;
    private final boolean ownedExecutor;

    private final Object stateLock = new Object();
    private final Map<String, LaneState> lanes = new HashMap<>();
    private final AtomicLong taskIdSeq = new AtomicLong(0L);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * @param name                 diagnostic name for this dispatcher (e.g. {@code "session"}, {@code "global"})
     * @param defaultMaxConcurrent default lane capacity when a lane is implicitly created; must be &gt;= 1
     */
    public LaneDispatcher(final String name, final int defaultMaxConcurrent) {
        this(name, defaultMaxConcurrent, Executors.newVirtualThreadPerTaskExecutor(), true);
    }

    public LaneDispatcher(final String name, final int defaultMaxConcurrent, final Executor executor) {
        this(name, defaultMaxConcurrent, executor, false);
    }

    private LaneDispatcher(final String name,
                           final int defaultMaxConcurrent,
                           final Executor executor,
                           final boolean ownedExecutor) {
        if (defaultMaxConcurrent < 1) {
            throw new IllegalArgumentException("defaultMaxConcurrent must be >= 1");
        }
        this.name = name == null ? "default" : name;
        this.defaultMaxConcurrent = defaultMaxConcurrent;
        this.executor = executor;
        this.ownedExecutor = ownedExecutor;
    }

    public String name() {
        return name;
    }

    public int defaultMaxConcurrent() {
        return defaultMaxConcurrent;
    }

    // ===================================================================================================
    // Public API
    // ===================================================================================================

    /**
     * Enqueue a task into the given lane. Returns a future that completes with the task's result.
     *
     * <p>Semantics:
     * <ul>
     *   <li>If lane is not yet active, it is implicitly created with {@link #defaultMaxConcurrent}.</li>
     *   <li>If the dispatcher is closed, the returned future is already-failed with {@link CancellationException}.</li>
     *   <li>If {@code options.warnAfterMs} elapses while still queued, {@link EnqueueOptions#onWait()} fires once.</li>
     * </ul>
     */
    public <T> CompletableFuture<T> enqueue(final String lane,
                                            final Supplier<? extends T> task,
                                            final EnqueueOptions options) {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        final String resolvedLane = lane == null ? CommandLane.MAIN.laneName() : lane;
        final CompletableFuture<T> promise = new CompletableFuture<>();
        if (closed.get()) {
            promise.completeExceptionally(new CancellationException("dispatcher closed"));
            return promise;
        }
        final QueueEntry<T> entry = new QueueEntry<>(taskIdSeq.incrementAndGet(),
                task,
                promise,
                System.currentTimeMillis(),
                options == null ? EnqueueOptions.DEFAULTS : options);

        synchronized (stateLock) {
            final LaneState state = lanes.computeIfAbsent(resolvedLane, k -> new LaneState(k, defaultMaxConcurrent));
            state.queue.addLast(entry);
        }
        drainLane(resolvedLane);
        return promise;
    }

    /**
     * Convenience overload with default {@link EnqueueOptions#DEFAULTS}.
     */
    public <T> CompletableFuture<T> enqueue(final String lane, final Supplier<? extends T> task) {
        return enqueue(lane, task, EnqueueOptions.DEFAULTS);
    }

    /**
     * Update a lane's maximum concurrency. Takes effect immediately: if capacity increased, queued tasks drain
     * right away; if decreased, inflight tasks finish first and only future pops respect the new cap.
     *
     * @throws IllegalArgumentException when {@code maxConcurrent < 1}
     */
    public void setMaxConcurrent(final String lane, final int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be >= 1");
        }
        final String resolvedLane = lane == null ? CommandLane.MAIN.laneName() : lane;
        synchronized (stateLock) {
            final LaneState state = lanes.computeIfAbsent(resolvedLane, k -> new LaneState(k, maxConcurrent));
            state.maxConcurrent = maxConcurrent;
        }
        drainLane(resolvedLane);
    }

    /**
     * Cancel all queued tasks in {@code lane}; inflight tasks are not affected.
     * Cancelled tasks fail with {@link CommandLaneClearedException}.
     *
     * @return number of queued tasks that were cancelled
     */
    public int clearLane(final String lane) {
        if (lane == null) {
            return 0;
        }
        final List<QueueEntry<?>> cancelled = new ArrayList<>();
        synchronized (stateLock) {
            final LaneState state = lanes.get(lane);
            if (state == null) {
                return 0;
            }
            cancelled.addAll(state.queue);
            state.queue.clear();
        }
        for (final QueueEntry<?> entry : cancelled) {
            entry.result.completeExceptionally(new CommandLaneClearedException(lane));
        }
        return cancelled.size();
    }

    /**
     * Reset every lane: bump {@code generation} on each, clear every queue, leave inflight tasks alone.
     * Completions arriving after this call are filtered by {@code ==} generation check and thus have no effect.
     *
     * <p>3-step contract (see TS {@code resetAllLanes}):
     * <ol>
     *   <li>Increment {@code generation} for every lane.</li>
     *   <li>Reject queued tasks with {@link CommandLaneClearedException} (reason includes the provided tag).</li>
     *   <li>Inflight tasks continue; their post-completion callbacks short-circuit because the lane state has
     *       moved on to a newer generation.</li>
     * </ol>
     *
     * @param reason human-readable reason (for logs / diagnostics)
     * @return total number of queued tasks cancelled across all lanes
     */
    public int resetAllLanes(final String reason) {
        final List<QueueEntry<?>> cancelled = new ArrayList<>();
        synchronized (stateLock) {
            for (final LaneState state : lanes.values()) {
                state.generation++;
                cancelled.addAll(state.queue);
                state.queue.clear();
            }
        }
        final String tag = reason == null || reason.isBlank() ? "resetAllLanes" : reason;
        for (final QueueEntry<?> entry : cancelled) {
            entry.result.completeExceptionally(new CommandLaneClearedException(tag));
        }
        log.info("lane.reset dispatcher={} reason={} cancelled={}", name, tag, cancelled.size());
        return cancelled.size();
    }

    /**
     * Busy-wait (polling every {@value #POLL_INTERVAL_MS} ms) until all inflight tasks finish or the timeout
     * elapses.
     *
     * @return {@code true} when all lanes are idle, {@code false} on timeout
     */
    public boolean waitForActiveTasks(final long timeoutMs) {
        final long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (true) {
            synchronized (stateLock) {
                boolean allIdle = true;
                for (final LaneState state : lanes.values()) {
                    if (!state.activeTaskIds.isEmpty()) {
                        allIdle = false;
                        break;
                    }
                }
                if (allIdle) {
                    return true;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * Observation snapshot (copy, never a live reference).
     */
    public LaneSnapshot snapshot(final String lane) {
        synchronized (stateLock) {
            final LaneState state = lanes.get(lane);
            if (state == null) {
                return new LaneSnapshot(lane, 0, 0, defaultMaxConcurrent, 0, false);
            }
            return new LaneSnapshot(state.lane,
                    state.queue.size(),
                    state.activeTaskIds.size(),
                    state.maxConcurrent,
                    state.generation,
                    state.draining);
        }
    }

    public List<LaneSnapshot> snapshots() {
        synchronized (stateLock) {
            return lanes.values().stream()
                    .map(state -> new LaneSnapshot(state.lane,
                            state.queue.size(),
                            state.activeTaskIds.size(),
                            state.maxConcurrent,
                            state.generation,
                            state.draining))
                    .sorted(Comparator.comparing(LaneSnapshot::lane))
                    .toList();
        }
    }

    /**
     * Map-based transform over all lane snapshots; useful for metrics export.
     */
    public <R> List<R> mapSnapshots(final Function<LaneSnapshot, R> fn) {
        return snapshots().stream().map(fn).toList();
    }

    // ===================================================================================================
    // Close
    // ===================================================================================================

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        final List<QueueEntry<?>> cancelled = new ArrayList<>();
        synchronized (stateLock) {
            for (final LaneState state : lanes.values()) {
                cancelled.addAll(state.queue);
                state.queue.clear();
            }
        }
        for (final QueueEntry<?> entry : cancelled) {
            entry.result.completeExceptionally(new CancellationException("dispatcher closed"));
        }
        if (ownedExecutor && executor instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("dispatcher.close executorClose failed dispatcher={} err={}", name, e.toString());
            }
        }
    }

    // ===================================================================================================
    // Internal
    // ===================================================================================================

    /**
     * Pop runnable tasks from the given lane while capacity is available.
     * <p>
     * Guarded by {@code draining=true} so recursive drain requests (from task completions on the same thread)
     * collapse into the outer drain; the flag is reset in a finally block so exceptions never freeze the lane.
     */
    private void drainLane(final String lane) {
        final List<Runnable> ready = new ArrayList<>();
        synchronized (stateLock) {
            final LaneState state = lanes.get(lane);
            if (state == null || state.draining) {
                return;
            }
            state.draining = true;
            try {
                while (!state.queue.isEmpty() && state.activeTaskIds.size() < state.maxConcurrent) {
                    final QueueEntry<?> entry = state.queue.pollFirst();
                    if (entry == null) {
                        break;
                    }
                    final long genSnapshot = state.generation;
                    state.activeTaskIds.add(entry.id);
                    ready.add(() -> runEntry(lane, state, entry, genSnapshot));
                }
            } finally {
                state.draining = false;
            }
        }
        for (final Runnable runnable : ready) {
            try {
                executor.execute(runnable);
            } catch (RuntimeException ex) {
                log.error("lane.executor rejected dispatcher={} lane={} err={}", name, lane, ex.toString());
                throw ex;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void runEntry(final String lane,
                          final LaneState state,
                          final QueueEntry<?> entry,
                          final long genAtDispatch) {
        // Fire wait-warning if the task waited too long before we actually ran it.
        final long waitedMs = System.currentTimeMillis() - entry.enqueuedAt;
        if (!entry.warnFired && waitedMs >= entry.options.warnAfterMs()) {
            entry.warnFired = true;
            if (!LaneNames.isProbeLane(lane)) {
                log.warn("lane.wait dispatcher={} lane={} taskId={} waitedMs={}",
                        name, lane, entry.id, waitedMs);
            }
            try {
                if (entry.options.onWait() != null) {
                    entry.options.onWait().accept(waitedMs);
                }
            } catch (RuntimeException onWaitEx) {
                if (!LaneNames.isProbeLane(lane)) {
                    log.error("lane.onWait failed dispatcher={} lane={} err={}", name, lane, onWaitEx.toString());
                }
            }
        }

        Object value = null;
        Throwable failure = null;
        try {
            value = entry.task.get();
        } catch (Throwable t) {
            failure = t;
            if (!LaneNames.isProbeLane(lane)) {
                log.error("lane.task failed dispatcher={} lane={} taskId={} err={}",
                        name, lane, entry.id, t.toString());
            }
        }

        synchronized (stateLock) {
            state.activeTaskIds.remove(entry.id);
        }

        final CompletableFuture future = entry.result;
        if (failure != null) {
            final Throwable unwrapped = (failure instanceof CompletionException ce && ce.getCause() != null)
                    ? ce.getCause() : failure;
            future.completeExceptionally(unwrapped);
        } else {
            future.complete(value);
        }

        // Guard: if the lane has been reset since dispatch, do NOT drain further — let the new generation start fresh.
        synchronized (stateLock) {
            if (state.generation != genAtDispatch) {
                return;
            }
        }
        drainLane(lane);
    }

}
