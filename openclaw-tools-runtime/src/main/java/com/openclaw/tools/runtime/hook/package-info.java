/**
 * {@code before_tool_call} / {@code after_tool_call} hook plumbing:
 * {@link com.openclaw.tools.runtime.hook.BeforeToolCallHookWrapper} (wraps tools to run the modifying
 * before-hook and merge params), {@link com.openclaw.tools.runtime.hook.AfterToolCallHookEmitter}
 * (fire-and-forget after-hook), and {@link com.openclaw.tools.runtime.hook.AdjustedParamsStore}
 * (correlates the two by {@code toolCallId}, bounded LRU).
 */
package com.openclaw.tools.runtime.hook;
