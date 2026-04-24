package com.openclaw.tools.runtime.hook;

import java.util.Map;
import java.util.Objects;

import com.openclaw.tools.ToolContext;
import com.openclaw.tools.ToolResult;

/**
 * Input payload for {@link com.openclaw.hooks.HookNames#AFTER_TOOL_CALL} hooks. Fired fire-and-forget on both
 * the success and failure paths (plan §13.2 step 2).
 *
 * <p>{@link #params()} is the <b>post-before-hook</b> view (fetched via
 * {@link AdjustedParamsStore#consumeForToolCall(String)}); using the raw original params would give
 * observers an outdated picture.
 */
public record AfterToolCallEvent(String toolName,
                                 String toolCallId,
                                 Map<String, Object> params,
                                 ToolResult result,
                                 String errorMessage,
                                 long durationMs,
                                 ToolContext context) {

    public AfterToolCallEvent {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(context, "context");
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public boolean isSuccess() {
        return result != null && result.isSuccess();
    }

    public boolean isError() {
        return errorMessage != null || (result != null && result.isError());
    }
}
