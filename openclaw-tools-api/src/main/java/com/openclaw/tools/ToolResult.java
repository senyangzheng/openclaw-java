package com.openclaw.tools;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Result emitted by a tool execution. Mirrors openclaw-ts {@code ToolResult}: one of a successful
 * {@link #content} payload or an {@link #error}; never both.
 *
 * @param content     primary payload to feed back into the model (string / json-encodable map).
 *                    {@code null} iff {@link #error} is present.
 * @param error       error message (already sanitized — no stack traces!) when execution failed.
 *                    {@code null} on success.
 * @param metadata    optional side-channel data (counts, durations, inferred citations...) not round-tripped
 *                    back to the model.
 * @param durationMs  wall-clock duration, filled by the runtime, {@code 0} when unknown.
 */
public record ToolResult(Object content,
                         String error,
                         Map<String, Object> metadata,
                         long durationMs) {

    public ToolResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public boolean isError() {
        return error != null;
    }

    public static ToolResult ok(final Object content) {
        return new ToolResult(content, null, Map.of(), 0L);
    }

    public static ToolResult ok(final Object content, final Duration duration) {
        return new ToolResult(content, null, Map.of(), duration == null ? 0L : duration.toMillis());
    }

    public static ToolResult fail(final String error) {
        Objects.requireNonNull(error, "error");
        return new ToolResult(null, error, Map.of(), 0L);
    }

    /** Returns a copy with the given duration; used by the runtime after timing the executor. */
    public ToolResult withDurationMs(final long newDurationMs) {
        return new ToolResult(content, error, metadata, newDurationMs);
    }
}
