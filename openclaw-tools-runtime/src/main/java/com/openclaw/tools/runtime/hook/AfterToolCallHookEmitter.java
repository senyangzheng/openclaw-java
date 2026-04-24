package com.openclaw.tools.runtime.hook;

import java.util.Map;
import java.util.Objects;

import com.openclaw.hooks.HookContext;
import com.openclaw.hooks.HookNames;
import com.openclaw.hooks.HookRunner;
import com.openclaw.tools.ToolContext;
import com.openclaw.tools.ToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fire-and-forget emitter for {@link HookNames#AFTER_TOOL_CALL} (see plan §13.2 step 2). Mirrors
 * openclaw-ts {@code handleToolExecutionEnd}: called on both the success and failure paths with
 * {@code durationMs}; must NOT propagate handler failures — swallowed at {@code log.debug}.
 *
 * <p>Accepts the post-before-hook params via {@link AdjustedParamsStore#consumeForToolCall(String)}: when
 * a {@code before_tool_call} hook rewrote params, observers see the rewritten values; when no rewrite
 * happened, {@code originalParams} is used instead.
 */
public final class AfterToolCallHookEmitter {

    private static final Logger log = LoggerFactory.getLogger(AfterToolCallHookEmitter.class);

    private final HookRunner hookRunner;
    private final AdjustedParamsStore store;

    public AfterToolCallHookEmitter(final HookRunner hookRunner, final AdjustedParamsStore store) {
        this.hookRunner = Objects.requireNonNull(hookRunner, "hookRunner");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Success path. {@code result} must not be {@code null}. */
    public void emitSuccess(final String toolName,
                            final String toolCallId,
                            final Map<String, Object> originalParams,
                            final ToolResult result,
                            final long durationMs,
                            final ToolContext context) {
        Objects.requireNonNull(result, "result");
        final Map<String, Object> params = resolveParams(toolCallId, originalParams);
        final AfterToolCallEvent evt = new AfterToolCallEvent(
                toolName, toolCallId, params,
                result.withDurationMs(durationMs),
                null,
                durationMs, context);
        fireSafely(evt);
    }

    /** Failure path — called from the {@code catch} block of the executor. */
    public void emitFailure(final String toolName,
                            final String toolCallId,
                            final Map<String, Object> originalParams,
                            final Throwable error,
                            final long durationMs,
                            final ToolContext context) {
        final Map<String, Object> params = resolveParams(toolCallId, originalParams);
        final String msg = error == null
                ? "unknown error"
                : (error.getMessage() != null ? error.getMessage() : error.toString());
        final AfterToolCallEvent evt = new AfterToolCallEvent(
                toolName, toolCallId, params,
                null,
                msg,
                durationMs, context);
        fireSafely(evt);
    }

    private Map<String, Object> resolveParams(final String toolCallId,
                                               final Map<String, Object> originalParams) {
        return store.consumeForToolCall(toolCallId).orElseGet(
                () -> originalParams == null ? Map.of() : Map.copyOf(originalParams));
    }

    private void fireSafely(final AfterToolCallEvent evt) {
        try {
            hookRunner.runVoidHook(HookNames.AFTER_TOOL_CALL, evt,
                    HookContext.of(HookNames.AFTER_TOOL_CALL));
        } catch (RuntimeException ex) {
            // runVoidHook already isolates handler failures; this catch covers rare
            // scheduling / executor shutdown failures.
            log.debug("after_tool_call.emit swallowed err={}", ex.toString());
        }
    }
}
