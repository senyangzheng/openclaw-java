package com.openclaw.hooks;

import java.util.Map;

/**
 * Static metadata passed alongside the event to a hook handler.
 * Keeps handler signatures forward-compatible as we add new fields over time.
 *
 * @param hookName canonical name (e.g. {@code "before_tool_call"}); see {@link HookNames}
 * @param metadata free-form non-PII metadata propagated from the invoker (e.g. {@code requestId}, {@code sessionKey})
 */
public record HookContext(String hookName, Map<String, Object> metadata) {

    public HookContext {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static HookContext of(final String hookName) {
        return new HookContext(hookName, Map.of());
    }

    public static HookContext of(final String hookName, final Map<String, Object> metadata) {
        return new HookContext(hookName, metadata);
    }
}
