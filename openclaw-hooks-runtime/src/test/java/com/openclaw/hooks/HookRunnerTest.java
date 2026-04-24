package com.openclaw.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.openclaw.common.error.OpenClawException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HookRunnerTest {

    private HookRunner runner;

    @BeforeEach
    void setUp() {
        runner = new HookRunner();
    }

    @AfterEach
    void tearDown() {
        runner.close();
    }

    // ================================================================================
    // §16 #1 — priority descending, registration-time ascending as tiebreaker
    // ================================================================================

    @Test
    void modifying_runsInPriorityDescendingAndRegistrationOrder() {
        final CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();
        runner.registerModifying("h", "low", 10, (evt, ctx) -> {
            order.add("low");
            return HookOutcome.EMPTY;
        });
        runner.registerModifying("h", "high-first", 100, (evt, ctx) -> {
            order.add("high-first");
            return HookOutcome.EMPTY;
        });
        runner.registerModifying("h", "high-second", 100, (evt, ctx) -> {
            order.add("high-second");
            return HookOutcome.EMPTY;
        });
        runner.registerModifying("h", "mid", 50, (evt, ctx) -> {
            order.add("mid");
            return HookOutcome.EMPTY;
        });

        runner.runModifyingHook("h", "evt", null, new HashMap<String, Object>(),
                (acc, delta) -> acc);
        assertThat(order).containsExactly("high-first", "high-second", "mid", "low");
    }

    // ================================================================================
    // §16 #2 — runVoidHook runs all in parallel, waits for completion
    // ================================================================================

    @Test
    void voidHooks_runInParallel() throws Exception {
        final int n = 4;
        final CountDownLatch started = new CountDownLatch(n);
        final CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < n; i++) {
            final String id = "h" + i;
            runner.registerVoid("evt", id, 0, (event, ctx) -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        final CompletableFuture<Void> f = runner.runVoidHook("evt", "e", null);
        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        f.get(3, TimeUnit.SECONDS);
    }

    // ================================================================================
    // §16 #3 — catchErrors true by default: one failing handler doesn't break chain
    // ================================================================================

    @Test
    void modifying_catchErrorsTrue_skipsFailingHandler() {
        runner.registerModifying("h", "boom", 50, (evt, ctx) -> {
            throw new IllegalStateException("boom");
        });
        runner.registerModifying("h", "after", 40, (evt, ctx) ->
                HookOutcome.modify(Map.of("seen", true)));

        final ModifyingHookResult<Map<String, Object>> result = runner.runModifyingHook("h", "evt", null,
                new HashMap<>(),
                (acc, delta) -> {
                    acc.putAll(delta);
                    return acc;
                });

        assertThat(result.isShortCircuit()).isFalse();
        assertThat(result.accumulator()).containsEntry("seen", true);
        assertThat(runner.diagnostics().snapshot())
                .anyMatch(e -> e.handlerId().equals("boom") && e.severity().equals("ERROR"));
    }

    @Test
    void modifying_catchErrorsFalse_propagates() {
        runner.registerModifying("h", "boom", 50, (evt, ctx) -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(() -> runner.runModifyingHook("h", "e", null, new HashMap<>(),
                (acc, delta) -> acc,
                /*catchErrors=*/false))
                .isInstanceOf(java.util.concurrent.CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidHooks_isolateHandlerFailures() throws Exception {
        runner.registerVoid("v", "boom", 10, (evt, ctx) -> {
            throw new IllegalStateException("bad");
        });
        final CompletableFuture<Void> fut = runner.runVoidHook("v", null, null);
        fut.get(2, TimeUnit.SECONDS);
        assertThat(runner.diagnostics().snapshot())
                .anyMatch(e -> e.handlerId().equals("boom"));
    }

    // ================================================================================
    // HookOutcome tri-state — Modify / Block / ShortCircuit
    // ================================================================================

    @Test
    void modifying_mergeDeltasInOrder() {
        runner.registerModifying("h", "a", 100, (e, c) -> HookOutcome.modify(Map.of("k", "a")));
        runner.registerModifying("h", "b", 50, (e, c) -> HookOutcome.modify(Map.of("k", "b")));

        final ModifyingHookResult<Map<String, Object>> result = runner.runModifyingHook("h", null, null,
                new HashMap<>(),
                (acc, delta) -> {
                    acc.putAll(delta);
                    return acc;
                });
        assertThat(result.isShortCircuit()).isFalse();
        // b overwrites a because b runs AFTER a (lower priority)
        assertThat(result.accumulator()).containsEntry("k", "b");
    }

    @Test
    void modifying_blockHaltsChainAndThrows() {
        final CopyOnWriteArrayList<String> seen = new CopyOnWriteArrayList<>();
        runner.registerModifying("h", "first", 100, (e, c) -> {
            seen.add("first");
            return HookOutcome.block("not allowed");
        });
        runner.registerModifying("h", "second", 50, (e, c) -> {
            seen.add("second");
            return HookOutcome.EMPTY;
        });

        assertThatThrownBy(() -> runner.runModifyingHook("h", null, null, new HashMap<>(), (acc, delta) -> acc))
                .isInstanceOfSatisfying(HookBlockedException.class, ex -> {
                    assertThat(ex.hookName()).isEqualTo("h");
                    assertThat(ex.handlerId()).isEqualTo("first");
                    assertThat(ex.blockReason()).isEqualTo("not allowed");
                });
        assertThat(seen).containsExactly("first"); // second never ran
    }

    @Test
    void modifying_shortCircuitHaltsChainButCallerUsesReply() {
        final CopyOnWriteArrayList<String> seen = new CopyOnWriteArrayList<>();
        runner.registerModifying("h", "sc", 100, (e, c) -> {
            seen.add("sc");
            return HookOutcome.shortCircuit("Hi from HelloPlugin!");
        });
        runner.registerModifying("h", "skipped", 50, (e, c) -> {
            seen.add("skipped");
            return HookOutcome.EMPTY;
        });

        final ModifyingHookResult<Map<String, Object>> result = runner.runModifyingHook("h", null, null,
                new HashMap<>(),
                (acc, delta) -> acc);
        assertThat(result.isShortCircuit()).isTrue();
        assertThat(result.shortCircuit()).isEqualTo("Hi from HelloPlugin!");
        assertThat(seen).containsExactly("sc");
    }

    // ================================================================================
    // §16 #6 — registration conflict records diagnostics and throws
    // ================================================================================

    @Test
    void register_duplicateHandlerIdRejectedAndRecorded() {
        runner.registerModifying("h", "dup", 0, (e, c) -> HookOutcome.EMPTY);
        assertThatThrownBy(() -> runner.registerModifying("h", "dup", 0, (e, c) -> HookOutcome.EMPTY))
                .isInstanceOf(OpenClawException.class)
                .hasMessageContaining("Duplicate");

        final List<HookDiagnostics.Entry> snap = runner.diagnostics().snapshot();
        assertThat(snap).anyMatch(e -> e.handlerId().equals("dup") && e.severity().equals("ERROR"));
    }

    @Test
    void unregister_removesHandler() {
        final HookRegistration reg = runner.registerVoid("v", "x", 0, (e, c) -> {
        });
        assertThat(runner.sizeOf("v")).isEqualTo(1);
        assertThat(runner.unregister(reg)).isTrue();
        assertThat(runner.sizeOf("v")).isEqualTo(0);
    }

    // ================================================================================
    // Edge case — no handlers returns empty immediately
    // ================================================================================

    @Test
    void emptyChain_returnsInitialAccumulator() {
        final ModifyingHookResult<String> result = runner.runModifyingHook("absent", null, null, "acc0",
                (acc, delta) -> acc);
        assertThat(result.isShortCircuit()).isFalse();
        assertThat(result.accumulator()).isEqualTo("acc0");
    }

    @Test
    void voidChain_emptyCompletesImmediately() throws Exception {
        runner.runVoidHook("absent", null, null).get(1, TimeUnit.SECONDS);
    }
}
