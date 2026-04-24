package com.openclaw.hooks;

/**
 * Functional interface for modifying hook handlers (before_agent_start / before_tool_call).
 *
 * @param <E> the event type carried by this hook (e.g. {@code BeforeAgentStartEvent})
 */
@FunctionalInterface
public interface ModifyingHookHandler<E> {

    /**
     * @param event the triggering event; immutable from the handler's perspective
     * @param ctx   hook metadata (hook name + optional diagnostic info)
     * @return one of {@link HookOutcome.Modify} / {@link HookOutcome.Block} / {@link HookOutcome.ShortCircuit}
     */
    HookOutcome handle(E event, HookContext ctx);
}
