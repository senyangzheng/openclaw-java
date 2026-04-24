package com.openclaw.tools.runtime.hook;

import java.util.Map;
import java.util.Objects;

import com.openclaw.tools.ToolContext;

/**
 * Input payload for {@link com.openclaw.hooks.HookNames#BEFORE_TOOL_CALL} hooks.
 *
 * <p>Plugins can return a {@link com.openclaw.hooks.HookOutcome.Modify} with the delta keys:
 * <ul>
 *   <li>{@code "params"} ({@code Map<String,Object>}) — rewrites the effective params. Single-plugin merge
 *       is {@code {...originalParams, ...delta.params}}; multi-plugin merge is last-write-wins
 *       ({@code next.params ?? acc.params}).</li>
 *   <li>{@code "block"} ({@link Boolean}) + {@code "blockReason"} ({@link String}) — short-circuit the
 *       execution with {@link com.openclaw.tools.ToolErrorCode#TOOL_BLOCKED_BY_HOOK}.</li>
 * </ul>
 *
 * <p>Mirrors openclaw-ts {@code BeforeToolCallHookInput}.
 */
public record BeforeToolCallEvent(String toolName,
                                  String toolCallId,
                                  Map<String, Object> params,
                                  ToolContext context) {

    public BeforeToolCallEvent {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(context, "context");
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
