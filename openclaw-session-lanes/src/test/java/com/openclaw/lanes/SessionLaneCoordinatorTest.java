package com.openclaw.lanes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionLaneCoordinatorTest {

    private SessionLaneCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new SessionLaneCoordinator();
        coordinator.applyGlobalConcurrency(new GlobalLaneConcurrency(1, 2, 2));
    }

    @AfterEach
    void tearDown() {
        coordinator.close();
    }

    @Test
    void run_sameSessionIsSerialized() throws Exception {
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxSeen = new AtomicInteger();
        final int n = 6;
        final List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(coordinator.run("acct:1:conv:x", CommandLane.MAIN, () -> {
                final int now = concurrent.incrementAndGet();
                maxSeen.updateAndGet(prev -> Math.max(prev, now));
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                concurrent.decrementAndGet();
                return idx;
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
        assertThat(maxSeen.get()).isEqualTo(1);
    }

    @Test
    void run_differentSessionsCanBeConcurrent_butBoundByGlobalLaneCap() throws Exception {
        // global main cap = 2
        final int mainCap = 2;
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxSeen = new AtomicInteger();
        final int n = 5;
        final CountDownLatch release = new CountDownLatch(1);
        final List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(coordinator.run("acct:" + idx, CommandLane.MAIN, () -> {
                final int now = concurrent.incrementAndGet();
                maxSeen.updateAndGet(prev -> Math.max(prev, now));
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                concurrent.decrementAndGet();
                return idx;
            }));
        }
        Thread.sleep(100);
        assertThat(maxSeen.get()).isEqualTo(mainCap);
        release.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
    }

    @Test
    void run_fallsBackToMainWhenGlobalLaneIsNull() throws Exception {
        final CompletableFuture<String> f = coordinator.run("acct:a", null, () -> "ok");
        assertThat(f.get(2, TimeUnit.SECONDS)).isEqualTo("ok");
    }
}
