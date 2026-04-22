package com.openclaw.channels.core;

import com.openclaw.routing.RoutingKey;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A text reply produced by the auto-reply pipeline, ready to hand back to the channel.
 */
public record OutboundMessage(
    String messageId,
    RoutingKey routingKey,
    String text,
    String replyToMessageId,
    Instant producedAt
) {

    public OutboundMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(routingKey, "routingKey");
        Objects.requireNonNull(text, "text");
        producedAt = producedAt == null ? Instant.now() : producedAt;
    }

    public static OutboundMessage replyTo(final InboundMessage inbound, final String text) {
        return new OutboundMessage(
            UUID.randomUUID().toString(),
            inbound.routingKey(),
            text,
            inbound.messageId(),
            Instant.now()
        );
    }
}
