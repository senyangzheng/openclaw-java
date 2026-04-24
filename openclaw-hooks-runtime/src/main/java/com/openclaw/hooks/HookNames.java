package com.openclaw.hooks;

/**
 * Canonical built-in hook injection point names.
 *
 * <p>Plugins may register at other names, but these four are reserved for framework hooks and are the only
 * names referenced by {@code openclaw-agents-core} / {@code openclaw-tools} / {@code openclaw-approval}.
 *
 * <p>See {@code .cursor/plan/05-translation-conventions.md} §16 #4.
 */
public final class HookNames {

    /** Invoked <i>before</i> the agent session prompt is emitted; modifying hook; supports short-circuit. */
    public static final String BEFORE_AGENT_START = "before_agent_start";

    /** Invoked <i>before</i> a tool's {@code execute} call; modifying hook; may adjust params or block. */
    public static final String BEFORE_TOOL_CALL = "before_tool_call";

    /** Invoked <i>after</i> a tool call (success or failure). Void / fire-and-forget. */
    public static final String AFTER_TOOL_CALL = "after_tool_call";

    /** Invoked when an agent attempt exits (success or error). Void / fire-and-forget. */
    public static final String RUN_AGENT_END = "run_agent_end";

    private HookNames() {
    }
}
