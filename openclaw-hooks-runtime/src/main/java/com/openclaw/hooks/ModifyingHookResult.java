package com.openclaw.hooks;

import java.util.Optional;

/**
 * Aggregate result of running a modifying hook chain.
 *
 * <p>Two possible shapes (see {@link HookOutcome}):
 * <ul>
 *   <li>{@code continueChain(acc)} — all handlers returned {@link HookOutcome.Modify}; the caller proceeds
 *       with the main branch using {@code acc}</li>
 *   <li>{@code shortCircuit(reply, acc)} — a handler returned {@link HookOutcome.ShortCircuit}; the caller
 *       <b>must</b> skip the main branch and return {@code reply} directly</li>
 * </ul>
 *
 * <p>{@code HookOutcome.Block} is <i>not</i> represented here — it is thrown as {@link HookBlockedException}.
 *
 * @param accumulator  the merged accumulator ({@code initial} plus every {@code Modify} delta)
 * @param shortCircuit {@code null} in the continue path; non-null when a hook short-circuited
 */
public record ModifyingHookResult<A>(A accumulator, String shortCircuit) {

    public static <A> ModifyingHookResult<A> continueChain(final A acc) {
        return new ModifyingHookResult<>(acc, null);
    }

    public static <A> ModifyingHookResult<A> shortCircuit(final String reply, final A acc) {
        return new ModifyingHookResult<>(acc, reply == null ? "" : reply);
    }

    public boolean isShortCircuit() {
        return shortCircuit != null;
    }

    public Optional<String> shortCircuitReply() {
        return Optional.ofNullable(shortCircuit);
    }
}
