package com.openclaw.lanes;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Dual-layer lane coordinator: session (outer, sequential per session) × global (inner, Cron / Main / Subagent
 * capacity cap).
 *
 * <p>Mirrors openclaw TS {@code withSessionAndGlobalLane} / {@code applyGatewayLaneConcurrency} from
 * {@code src/process/command-queue.ts} and {@code src/gateway/server-lanes.ts}.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Session-lane capacity fixed at 1 (guarantees in-session serialization).</li>
 *   <li>Global-lane capacity is configurable per {@link CommandLane}.</li>
 *   <li>A single logical task runs as {@code sessionLane.enqueue(() -> globalLane.enqueue(task).join())}, so the
 *       session slot is held for the full duration including the global-lane wait.</li>
 * </ul>
 *
 * <p>See {@code .cursor/plan/04-milestones.md} M3.3.
 */
public final class SessionLaneCoordinator implements AutoCloseable {

    private final LaneDispatcher sessionDispatcher;
    private final LaneDispatcher globalDispatcher;
    private final boolean ownsSession;
    private final boolean ownsGlobal;

    public SessionLaneCoordinator() {
        this(new LaneDispatcher("session", 1), new LaneDispatcher("global", 2), true, true);
    }

    public SessionLaneCoordinator(final LaneDispatcher sessionDispatcher,
                                  final LaneDispatcher globalDispatcher) {
        this(sessionDispatcher, globalDispatcher, false, false);
    }

    private SessionLaneCoordinator(final LaneDispatcher sessionDispatcher,
                                   final LaneDispatcher globalDispatcher,
                                   final boolean ownsSession,
                                   final boolean ownsGlobal) {
        if (sessionDispatcher == null) {
            throw new IllegalArgumentException("sessionDispatcher is required");
        }
        if (globalDispatcher == null) {
            throw new IllegalArgumentException("globalDispatcher is required");
        }
        this.sessionDispatcher = sessionDispatcher;
        this.globalDispatcher = globalDispatcher;
        this.ownsSession = ownsSession;
        this.ownsGlobal = ownsGlobal;
        this.sessionDispatcher.setMaxConcurrent(CommandLane.MAIN.laneName(), 1); // defensive
    }

    public LaneDispatcher sessionDispatcher() {
        return sessionDispatcher;
    }

    public LaneDispatcher globalDispatcher() {
        return globalDispatcher;
    }

    /**
     * Apply global-lane concurrency caps for Cron / Main / Subagent from a configuration holder.
     *
     * <p>Mirrors {@code applyGatewayLaneConcurrency(config)} in TS.
     */
    public void applyGlobalConcurrency(final GlobalLaneConcurrency concurrency) {
        globalDispatcher.setMaxConcurrent(CommandLane.CRON.laneName(), concurrency.cron());
        globalDispatcher.setMaxConcurrent(CommandLane.MAIN.laneName(), concurrency.main());
        globalDispatcher.setMaxConcurrent(CommandLane.SUBAGENT.laneName(), concurrency.subagent());
    }

    /**
     * Run a task respecting dual-layer semantics.
     *
     * @param sessionKey     session identifier; transformed by {@link LaneNames#resolveSessionLane(String)}
     * @param globalLane     global lane category (null → {@link CommandLane#MAIN})
     * @param task           unit of work
     * @param sessionOptions queue options for the outer session lane; may be {@code null}
     * @param globalOptions  queue options for the inner global lane; may be {@code null}
     */
    public <T> CompletableFuture<T> run(final String sessionKey,
                                        final CommandLane globalLane,
                                        final Supplier<? extends T> task,
                                        final EnqueueOptions sessionOptions,
                                        final EnqueueOptions globalOptions) {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        final String resolvedSession = LaneNames.resolveSessionLane(sessionKey);
        final String resolvedGlobal = LaneNames.resolveGlobalLane(
                globalLane == null ? null : globalLane.laneName());
        final AtomicReference<CompletableFuture<T>> innerRef = new AtomicReference<>();

        // Outer session lane: once we get our turn, delegate to global and block until global completes.
        final CompletableFuture<T> outer = sessionDispatcher.enqueue(resolvedSession, () -> {
            final CompletableFuture<T> inner = globalDispatcher.enqueue(resolvedGlobal, task, globalOptions);
            innerRef.set(inner);
            try {
                return inner.join();
            } catch (RuntimeException ex) {
                // unwrap the CompletionException wrapper so callers see the real cause
                if (ex instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null) {
                    final Throwable cause = ce.getCause();
                    if (cause instanceof RuntimeException rte) {
                        throw rte;
                    }
                    throw new IllegalStateException(cause);
                }
                throw ex;
            }
        }, sessionOptions);

        // If the outer future is cancelled, propagate cancellation to the inner global-lane task.
        outer.whenComplete((val, err) -> {
            final CompletableFuture<T> inner = innerRef.get();
            if (inner != null && !inner.isDone() && outer.isCancelled()) {
                inner.cancel(true);
            }
        });
        return outer;
    }

    public <T> CompletableFuture<T> run(final String sessionKey,
                                        final CommandLane globalLane,
                                        final Supplier<? extends T> task) {
        return run(sessionKey, globalLane, task, EnqueueOptions.DEFAULTS, EnqueueOptions.DEFAULTS);
    }

    /**
     * Wait until both dispatchers report zero active tasks.
     */
    public boolean waitForIdle(final long timeoutMs) {
        final long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        if (!sessionDispatcher.waitForActiveTasks(Math.max(0L, deadline - System.currentTimeMillis()))) {
            return false;
        }
        return globalDispatcher.waitForActiveTasks(Math.max(0L, deadline - System.currentTimeMillis()));
    }

    public int resetAllLanes(final String reason) {
        final int s = sessionDispatcher.resetAllLanes(reason);
        final int g = globalDispatcher.resetAllLanes(reason);
        return s + g;
    }

    public List<LaneSnapshot> sessionSnapshots() {
        return sessionDispatcher.snapshots();
    }

    public List<LaneSnapshot> globalSnapshots() {
        return globalDispatcher.snapshots();
    }

    @Override
    public void close() {
        if (ownsSession) {
            sessionDispatcher.close();
        }
        if (ownsGlobal) {
            globalDispatcher.close();
        }
    }
}
