package com.openclaw.providers.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Non-streaming completion result. Streaming will come as {@code Flux<ChatChunkEvent>} in M2.
 */
public record ChatResponse(
    String provider,
    String model,
    String content,
    FinishReason finishReason,
    Usage usage,
    Duration elapsed
) {

    public ChatResponse {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(content, "content");
        finishReason = finishReason == null ? FinishReason.STOP : finishReason;
        usage = usage == null ? Usage.EMPTY : usage;
        elapsed = elapsed == null ? Duration.ZERO : elapsed;
    }

    public enum FinishReason {
        STOP, LENGTH, TOOL_CALLS, CONTENT_FILTER, ERROR
    }

    public record Usage(int promptTokens, int completionTokens, int totalTokens) {
        public static final Usage EMPTY = new Usage(0, 0, 0);
    }
}
