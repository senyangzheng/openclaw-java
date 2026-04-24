package com.openclaw.hooks;

import java.util.Map;

/**
 * Three-state result returned by modifying hooks.
 *
 * <p>Semantics (see {@code .cursor/plan/05-translation-conventions.md} §16 #7):
 * <ul>
 *   <li>{@link Modify} — returns a delta that is {@code merge(acc, delta)}-ed and passes through to the next hook</li>
 *   <li>{@link Block} — halts the hook chain and the main branch; translated to {@link HookBlockedException}</li>
 *   <li>{@link ShortCircuit} — halts the hook chain and <b>skips the main branch</b> entirely; the caller uses
 *       {@link ShortCircuit#reply()} directly as the result (e.g. {@code /hello} user commands return a canned
 *       reply without invoking the LLM)</li>
 * </ul>
 *
 * <p>Priority when a hook accidentally returns a combined result: {@code block > shortCircuit > modify}. The
 * canonical factories on this sealed interface guarantee well-formed results.
 */
public sealed interface HookOutcome permits HookOutcome.Modify, HookOutcome.Block, HookOutcome.ShortCircuit {

    /** Convenience constant representing "no changes, continue". */
    Modify EMPTY = new Modify(Map.of());

    /** Create a {@link Modify} with a merge delta; {@code null} delta is treated as empty. */
    static Modify modify(final Map<String, Object> delta) {
        return new Modify(delta == null ? Map.of() : Map.copyOf(delta));
    }

    /** Create a {@link Block} with a human-readable reason. */
    static Block block(final String reason) {
        return new Block(reason == null ? "" : reason);
    }

    /** Create a {@link ShortCircuit} with the reply the caller should return to the user. */
    static ShortCircuit shortCircuit(final String reply) {
        return new ShortCircuit(reply == null ? "" : reply);
    }

    /** Pass-through (continue chain) with an optional merge delta. */
    record Modify(Map<String, Object> delta) implements HookOutcome {
        public Modify {
            delta = delta == null ? Map.of() : Map.copyOf(delta);
        }
    }

    /** Halt chain + main branch; translated to {@link HookBlockedException}. */
    record Block(String reason) implements HookOutcome {
    }

    /** Halt chain; <b>skip main branch</b>; caller returns {@link #reply()} directly. */
    record ShortCircuit(String reply) implements HookOutcome {
    }
}
