package com.openclaw.channels.core;

import com.openclaw.routing.RoutingKey;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A text message flowing from a channel into the framework. M1 is text-only;
 * attachments / audio frames / tool payloads are deferred (see {@code .cursor/plan/01-tech-stack.md} §7).
 */
public record InboundMessage(
    String messageId,
    RoutingKey routingKey,
    String text,
    Instant receivedAt,
    Map<String, Object> headers
) {

    public InboundMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(routingKey, "routingKey");
        Objects.requireNonNull(text, "text");
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
