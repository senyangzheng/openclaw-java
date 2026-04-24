package com.openclaw.hooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable hook runner — independent of plugin lifecycle, shared by agents / tools / approval / gateway.
 *
 * <p>Implements the six hard invariants of {@code .cursor/plan/05-translation-conventions.md} §16:
 * <ol>
 *   <li><b>Sorting</b>: {@code priority} descending, registration-time ascending as tiebreaker (stable).</li>
 *   <li><b>Two execution models</b>: {@link #runVoidHook} parallel via {@link CompletableFuture#allOf},
 *       {@link #runModifyingHook} sequential; each delta merges via the caller-supplied merge function.</li>
 *   <li><b>{@code catchErrors=true} by default</b>: individual handler exceptions are logged but do not break
 *       the caller's main branch; set false only for debugging.</li>
 *   <li><b>Fixed injection points</b>: see {@link HookNames}.</li>
 *   <li><b>No "after-the-fact before checks"</b>: this class only runs what callers invoke; it is the caller's
 *       responsibility to call before-hooks at the correct timing.</li>
 *   <li><b>Conflict diagnostics</b>: registration failures go into {@link HookDiagnostics} and throw; no silent
 *       overwrite.</li>
 * </ol>
 *
 * <p>Additionally implements the three-state {@link HookOutcome} semantics:
 * <ul>
 *   <li>{@link HookOutcome.Modify} — merge delta, continue chain</li>
 *   <li>{@link HookOutcome.Block} — translated to {@link HookBlockedException} thrown from
 *       {@link #runModifyingHook}</li>
 *   <li>{@link HookOutcome.ShortCircuit} — returned via {@link ModifyingHookResult#shortCircuit()}; callers must
 *       check this before consuming {@link ModifyingHookResult#accumulator()}</li>
 * </ul>
 */
public final class HookRunner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HookRunner.class);

    /** Comparator: priority descending, then registerSeq ascending. */
    private static final Comparator<Registered<?>> ORDER = Comparator
            .comparingInt((Registered<?> r) -> r.registration.priority()).reversed()
            .thenComparingLong(r -> r.registration.registerSeq());

    private final Executor executor;
    private final boolean ownedExecutor;
    private final HookDiagnostics diagnostics;
    private final boolean defaultCatchErrors;

    private final Object lock = new Object();
    private final Map<String, List<Registered<?>>> byName = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(0L);

    public HookRunner() {
        this(Executors.newVirtualThreadPerTaskExecutor(), true, new HookDiagnostics(), true);
    }

    public HookRunner(final Executor executor) {
        this(executor, false, new HookDiagnostics(), true);
    }

    public HookRunner(final Executor executor,
                      final HookDiagnostics diagnostics,
                      final boolean defaultCatchErrors) {
        this(executor, false, diagnostics, defaultCatchErrors);
    }

    private HookRunner(final Executor executor,
                       final boolean ownedExecutor,
                       final HookDiagnostics diagnostics,
                       final boolean defaultCatchErrors) {
        this.executor = executor;
        this.ownedExecutor = ownedExecutor;
        this.diagnostics = diagnostics == null ? new HookDiagnostics() : diagnostics;
        this.defaultCatchErrors = defaultCatchErrors;
    }

    public HookDiagnostics diagnostics() {
        return diagnostics;
    }

    // ===================================================================================================
    // Registration
    // ===================================================================================================

    /**
     * Register a modifying handler. {@code handlerId} must be unique per hook name; duplicates are rejected
     * (via {@link HookErrorCode#HOOK_REGISTRATION_CONFLICT}) and recorded in {@link #diagnostics()}.
     */
    public <E> HookRegistration registerModifying(final String hookName,
                                                  final String handlerId,
                                                  final int priority,
                                                  final ModifyingHookHandler<E> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler is required");
        }
        return register(hookName, handlerId, priority, true, handler);
    }

    /**
     * Register a void handler. {@code handlerId} must be unique per hook name.
     */
    public <E> HookRegistration registerVoid(final String hookName,
                                             final String handlerId,
                                             final int priority,
                                             final VoidHookHandler<E> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler is required");
        }
        return register(hookName, handlerId, priority, false, handler);
    }

    public boolean unregister(final HookRegistration reg) {
        if (reg == null) {
            return false;
        }
        synchronized (lock) {
            final List<Registered<?>> list = byName.get(reg.hookName());
            if (list == null) {
                return false;
            }
            return list.removeIf(r -> r.registration.handlerId().equals(reg.handlerId())
                    && r.registration.registerSeq() == reg.registerSeq());
        }
    }

    public int sizeOf(final String hookName) {
        synchronized (lock) {
            final List<Registered<?>> list = byName.get(hookName);
            return list == null ? 0 : list.size();
        }
    }

    // ===================================================================================================
    // Execution — void (parallel)
    // ===================================================================================================

    /**
     * Run all {@link VoidHookHandler} registered for {@code hookName} in parallel. Completes when all handlers
     * finish or fail. Failures are isolated — never rethrown; callers can inspect {@link HookDiagnostics}.
     *
     * <p>Returns a future that completes normally when every handler has terminated. The future never fails:
     * individual errors are swallowed (logged + diagnostics recorded).
     */
    public <E> CompletableFuture<Void> runVoidHook(final String hookName, final E event, final HookContext ctx) {
        final HookContext resolvedCtx = ctx == null ? HookContext.of(hookName) : ctx;
        final List<Registered<?>> handlers = handlersSorted(hookName, false);
        if (handlers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        final List<CompletableFuture<Void>> futures = new ArrayList<>(handlers.size());
        for (final Registered<?> r : handlers) {
            @SuppressWarnings("unchecked")
            final VoidHookHandler<E> handler = (VoidHookHandler<E>) r.handler;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    handler.handle(event, resolvedCtx);
                } catch (RuntimeException ex) {
                    log.error("hook.void failed name={} handler={} err={}",
                            hookName, r.registration.handlerId(), ex.toString());
                    diagnostics.record("ERROR", hookName, r.registration.handlerId(),
                            "void handler threw: " + ex);
                }
            }, executor));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    // ===================================================================================================
    // Execution — modifying (sequential)
    // ===================================================================================================

    /**
     * Run all {@link ModifyingHookHandler} registered for {@code hookName} sequentially. Applies {@code merge}
     * to fold every {@link HookOutcome.Modify} delta; returns either:
     * <ul>
     *   <li>{@code ModifyingHookResult.continueChain(acc)} — all handlers returned {@code Modify}; main branch runs</li>
     *   <li>{@code ModifyingHookResult.shortCircuit(reply, acc)} — a handler returned {@code ShortCircuit}; caller
     *       must skip the main branch and return {@code reply} directly</li>
     * </ul>
     * On {@link HookOutcome.Block} the method throws {@link HookBlockedException} ({@code block > shortCircuit}
     * in priority).
     *
     * <p>If {@code catchErrors=true} (default), handler exceptions are logged and the chain continues with the
     * current accumulator unchanged. If {@code false}, the exception propagates and aborts the chain.
     */
    public <E, A> ModifyingHookResult<A> runModifyingHook(final String hookName,
                                                         final E event,
                                                         final HookContext ctx,
                                                         final A initial,
                                                         final BiFunction<A, Map<String, Object>, A> merge) {
        return runModifyingHook(hookName, event, ctx, initial, merge, defaultCatchErrors);
    }

    public <E, A> ModifyingHookResult<A> runModifyingHook(final String hookName,
                                                         final E event,
                                                         final HookContext ctx,
                                                         final A initial,
                                                         final BiFunction<A, Map<String, Object>, A> merge,
                                                         final boolean catchErrors) {
        if (merge == null) {
            throw new IllegalArgumentException("merge function is required");
        }
        final HookContext resolvedCtx = ctx == null ? HookContext.of(hookName) : ctx;
        final List<Registered<?>> handlers = handlersSorted(hookName, true);
        A acc = initial;
        for (final Registered<?> r : handlers) {
            @SuppressWarnings("unchecked")
            final ModifyingHookHandler<E> handler = (ModifyingHookHandler<E>) r.handler;
            final HookOutcome outcome;
            try {
                outcome = handler.handle(event, resolvedCtx);
            } catch (RuntimeException ex) {
                if (catchErrors) {
                    log.error("hook.modifying failed name={} handler={} err={} (continuing chain)",
                            hookName, r.registration.handlerId(), ex.toString());
                    diagnostics.record("ERROR", hookName, r.registration.handlerId(),
                            "modifying handler threw (isolated): " + ex);
                    continue;
                }
                throw new CompletionException(ex);
            }
            if (outcome == null) {
                continue;
            }
            // priority: block > shortCircuit > modify
            if (outcome instanceof HookOutcome.Block block) {
                throw new HookBlockedException(hookName, r.registration.handlerId(), block.reason());
            }
            if (outcome instanceof HookOutcome.ShortCircuit sc) {
                return ModifyingHookResult.shortCircuit(sc.reply(), acc);
            }
            if (outcome instanceof HookOutcome.Modify m) {
                acc = merge.apply(acc, m.delta());
            }
        }
        return ModifyingHookResult.continueChain(acc);
    }

    // ===================================================================================================
    // Lifecycle
    // ===================================================================================================

    @Override
    public void close() {
        if (ownedExecutor && executor instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("hookRunner.close executor close failed err={}", e.toString());
            }
        }
    }

    // ===================================================================================================
    // Internal
    // ===================================================================================================

    private <E> HookRegistration register(final String hookName,
                                          final String handlerId,
                                          final int priority,
                                          final boolean modifying,
                                          final Object handler) {
        if (hookName == null || hookName.isBlank()) {
            throw new IllegalArgumentException("hookName is required");
        }
        if (handlerId == null || handlerId.isBlank()) {
            throw new IllegalArgumentException("handlerId is required");
        }
        final HookRegistration reg = new HookRegistration(hookName, handlerId, priority,
                seq.incrementAndGet(), modifying);
        synchronized (lock) {
            final List<Registered<?>> list = byName.computeIfAbsent(hookName, k -> new ArrayList<>());
            for (final Registered<?> existing : list) {
                if (existing.registration.handlerId().equals(handlerId)) {
                    diagnostics.record("ERROR", hookName, handlerId, "duplicate handlerId rejected");
                    throw new com.openclaw.common.error.OpenClawException(
                            HookErrorCode.HOOK_REGISTRATION_CONFLICT,
                            "Duplicate hook handler id: " + hookName + "#" + handlerId);
                }
            }
            list.add(new Registered<>(reg, handler));
        }
        return reg;
    }

    private List<Registered<?>> handlersSorted(final String hookName, final boolean wantModifying) {
        synchronized (lock) {
            final List<Registered<?>> list = byName.get(hookName);
            if (list == null || list.isEmpty()) {
                return List.of();
            }
            // Copy out first, then sort — keeps iteration safe against concurrent registration.
            final List<Registered<?>> copy = new ArrayList<>(list.size());
            for (final Registered<?> r : list) {
                if (r.registration.modifying() == wantModifying) {
                    copy.add(r);
                }
            }
            copy.sort(ORDER);
            return copy;
        }
    }

    // holder for a single registration
    private record Registered<H>(HookRegistration registration, H handler) {
    }
}
