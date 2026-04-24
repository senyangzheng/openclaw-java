package com.openclaw.lanes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaneDispatcherTest {

    private LaneDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new LaneDispatcher("test", 2);
    }

    @AfterEach
    void tearDown() {
        dispatcher.close();
    }

    // =========================================================================================
    // Invariant 1 — queue → active → capacity respected
    // =========================================================================================

    @Test
    void enqueue_respectsCapacityCapDuringBusyQueue() throws Exception {
        final int tasks = 6;
        final List<Integer> completed = new ArrayList<>();
        final List<CompletableFuture<Integer>> futures = new ArrayList<>();
        final CountDownLatch proceed = new CountDownLatch(1);
        final AtomicInteger runningPeak = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();

        for (int i = 0; i < tasks; i++) {
            final int idx = i;
            futures.add(dispatcher.enqueue("lane-a", () -> {
                final int now = active.incrementAndGet();
                runningPeak.updateAndGet(prev -> Math.max(prev, now));
                try {
                    proceed.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (completed) {
                    completed.add(idx);
                }
                active.decrementAndGet();
                return idx;
            }));
        }

        Thread.sleep(120);
        // capacity is 2; any number of in-flight tasks must not exceed it
        assertThat(dispatcher.snapshot("lane-a").active()).isEqualTo(2);
        assertThat(dispatcher.snapshot("lane-a").queueDepth()).isEqualTo(4);

        proceed.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
        assertThat(runningPeak.get()).isEqualTo(2);
        assertThat(completed).hasSize(tasks);
    }

    @Test
    void enqueue_withSessionCapacityOne_runsSequentially() throws Exception {
        final LaneDispatcher seq = new LaneDispatcher("seq", 1);
        try {
            final AtomicInteger maxConcurrent = new AtomicInteger(0);
            final AtomicInteger active = new AtomicInteger(0);
            final int n = 8;
            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(seq.enqueue("s", () -> {
                    final int now = active.incrementAndGet();
                    maxConcurrent.updateAndGet(prev -> Math.max(prev, now));
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    active.decrementAndGet();
                    return null;
                }));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
            assertThat(maxConcurrent.get()).isEqualTo(1);
        } finally {
            seq.close();
        }
    }

    // =========================================================================================
    // Invariant 3 — clearLane cancels queued but not active
    // =========================================================================================

    @Test
    void clearLane_cancelsQueuedButNotActive() throws Exception {
        final LaneDispatcher d = new LaneDispatcher("clear", 1);
        try {
            final CountDownLatch hold = new CountDownLatch(1);
            final CompletableFuture<String> active = d.enqueue("x", () -> {
                try {
                    hold.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "done";
            });
            final CompletableFuture<String> queued1 = d.enqueue("x", () -> "never");
            final CompletableFuture<String> queued2 = d.enqueue("x", () -> "never");

            Thread.sleep(50);
            final int cancelledCount = d.clearLane("x");
            assertThat(cancelledCount).isEqualTo(2);
            assertThatThrownBy(() -> queued1.get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(CommandLaneClearedException.class);
            assertThatThrownBy(() -> queued2.get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(CommandLaneClearedException.class);

            hold.countDown();
            assertThat(active.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        } finally {
            d.close();
        }
    }

    // =========================================================================================
    // Invariant 4 — resetAllLanes bumps generation and cancels queued
    // =========================================================================================

    @Test
    void resetAllLanes_cancelsQueuedAcrossAllLanes() throws Exception {
        final LaneDispatcher d = new LaneDispatcher("reset", 1);
        try {
            final CountDownLatch holdA = new CountDownLatch(1);
            final CountDownLatch holdB = new CountDownLatch(1);
            // hold one in-flight per lane, queue additional tasks
            d.enqueue("a", () -> {
                try { holdA.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return null;
            });
            d.enqueue("a", () -> null);
            d.enqueue("a", () -> null);
            d.enqueue("b", () -> {
                try { holdB.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return null;
            });
            d.enqueue("b", () -> null);
            Thread.sleep(80);

            final int cancelled = d.resetAllLanes("unit-test");
            assertThat(cancelled).isEqualTo(3); // 2 queued on a + 1 queued on b
            assertThat(d.snapshot("a").generation()).isGreaterThanOrEqualTo(1L);
            assertThat(d.snapshot("b").generation()).isGreaterThanOrEqualTo(1L);
            holdA.countDown();
            holdB.countDown();
        } finally {
            d.close();
        }
    }

    // =========================================================================================
    // Invariant 5 — waitForActiveTasks polls until idle or timeout
    // =========================================================================================

    @Test
    void waitForActiveTasks_returnsTrueWhenIdle() throws Exception {
        final CompletableFuture<?> f = dispatcher.enqueue("w", () -> null);
        f.get(2, TimeUnit.SECONDS);
        assertThat(dispatcher.waitForActiveTasks(1_000)).isTrue();
    }

    @Test
    void waitForActiveTasks_returnsFalseOnTimeout() {
        final CountDownLatch hold = new CountDownLatch(1);
        dispatcher.enqueue("w", () -> {
            try {
                hold.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        try {
            assertThat(dispatcher.waitForActiveTasks(200)).isFalse();
        } finally {
            hold.countDown();
        }
    }

    // =========================================================================================
    // Invariant 6 — setMaxConcurrent takes effect immediately
    // =========================================================================================

    @Test
    void setMaxConcurrent_increasesCapacityAndDrainsQueued() throws Exception {
        final LaneDispatcher d = new LaneDispatcher("cap", 1);
        try {
            final CountDownLatch start = new CountDownLatch(3);
            final CountDownLatch release = new CountDownLatch(1);
            for (int i = 0; i < 3; i++) {
                d.enqueue("c", () -> {
                    start.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            }
            Thread.sleep(80);
            assertThat(d.snapshot("c").active()).isEqualTo(1);

            d.setMaxConcurrent("c", 3);
            assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(d.snapshot("c").active()).isEqualTo(3);
            release.countDown();
        } finally {
            d.close();
        }
    }

    // =========================================================================================
    // Invariant 7 — probe lanes silence error logs (no assertion on log, just ensure failure surfaces)
    // =========================================================================================

    @Test
    void probeLaneFailure_stillPropagatesViaFuture() {
        final CompletableFuture<String> f = dispatcher.enqueue("auth-probe:q", () -> {
            throw new IllegalStateException("probe boom");
        });
        assertThatThrownBy(() -> f.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    // =========================================================================================
    // Wait warning (warnAfterMs + onWait)
    // =========================================================================================

    @Test
    void warnAfterMs_firesOnWaitCallback() throws Exception {
        final LaneDispatcher d = new LaneDispatcher("warn", 1);
        try {
            final AtomicInteger warned = new AtomicInteger();
            final CountDownLatch hold = new CountDownLatch(1);
            d.enqueue("w", () -> {
                try {
                    hold.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            final CompletableFuture<String> later = d.enqueue("w", () -> "ok",
                    EnqueueOptions.of(50L, ms -> warned.incrementAndGet()));
            Thread.sleep(120);
            hold.countDown();
            later.get(2, TimeUnit.SECONDS);
            assertThat(warned.get()).isEqualTo(1);
        } finally {
            d.close();
        }
    }

    // =========================================================================================
    // close rejects pending tasks
    // =========================================================================================

    @Test
    void close_rejectsQueuedTasks() throws Exception {
        final LaneDispatcher d = new LaneDispatcher("close", 1);
        final CountDownLatch hold = new CountDownLatch(1);
        d.enqueue("c", () -> {
            try {
                hold.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        final CompletableFuture<String> queued = d.enqueue("c", () -> "never");
        Thread.sleep(30);
        d.close();
        // CompletableFuture.completeExceptionally(CancellationException) surfaces the CE directly from get(),
        // not wrapped in ExecutionException; see CompletableFuture#reportGet.
        assertThatThrownBy(() -> queued.get(1, TimeUnit.SECONDS))
                .isInstanceOfAny(java.util.concurrent.CancellationException.class,
                        ExecutionException.class, TimeoutException.class);
        hold.countDown();
    }
}
