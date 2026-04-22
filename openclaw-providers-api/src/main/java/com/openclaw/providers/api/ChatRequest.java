package com.openclaw.providers.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-agnostic chat completion request.
 * <p>
 * {@link #extras()} is for opaque pass-through parameters (e.g. {@code temperature},
 * {@code safetySettings}); each concrete provider picks the keys it understands and
 * ignores the rest — see {@code .cursor/plan/05-translation-conventions.md}.
 */
public record ChatRequest(
    String model,
    List<ChatMessage> messages,
    Map<String, Object> extras
) {

    public ChatRequest {
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    public static ChatRequest of(final String model, final List<ChatMessage> messages) {
        return new ChatRequest(model, messages, Map.of());
    }
}
