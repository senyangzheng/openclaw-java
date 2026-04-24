package com.openclaw.hooks;

/**
 * Functional interface for void hook handlers (after_tool_call / run_agent_end / etc.).
 * Returned nothing, runs in parallel with all siblings.
 *
 * @param <E> the event type carried by this hook
 */
@FunctionalInterface
public interface VoidHookHandler<E> {

    void handle(E event, HookContext ctx);
}
