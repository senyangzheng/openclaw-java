package com.openclaw.tools;

import java.util.Map;
import java.util.Objects;

/**
 * Parameters passed to {@link Tool#execute(ToolRequest, ToolContext)}. Immutable view over the model-emitted
 * arguments plus enough metadata that {@code before/after_tool_call} hooks and downstream policies can make
 * decisions without having to look at the surrounding attempt state.
 *
 * @param toolName   SPI name of the tool being invoked (matches {@link Tool#name()})
 * @param toolCallId stable id assigned by the provider for this call — used by
 *                   {@code AdjustedParamsStore} to correlate {@code before} and {@code after} hook pairs
 * @param params     raw params as produced by the model (or rewritten by a {@code before_tool_call} hook)
 */
public record ToolRequest(String toolName,
                          String toolCallId,
                          Map<String, Object> params) {

    public ToolRequest {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(toolCallId, "toolCallId");
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** Copy-on-write helper used by {@code before_tool_call} hooks to rewrite params. */
    public ToolRequest withParams(final Map<String, Object> newParams) {
        return new ToolRequest(toolName, toolCallId, newParams);
    }
}
