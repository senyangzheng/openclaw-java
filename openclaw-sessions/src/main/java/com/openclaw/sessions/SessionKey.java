package com.openclaw.sessions;

import com.openclaw.common.util.Strings;

import java.util.Objects;

/**
 * Uniquely identifies a conversational session across channels.
 * <p>
 * Format: {@code "<channelId>:<accountId>:<conversationId>"} (colon-joined),
 * e.g. {@code "web:anonymous:c-123"}. The string form is stable and safe to use
 * as MDC value / cache key / persistence PK.
 */
public record SessionKey(String channelId, String accountId, String conversationId) {

    public SessionKey {
        if (Strings.isBlank(channelId)) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        if (Strings.isBlank(accountId)) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        if (Strings.isBlank(conversationId)) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
    }

    public String asString() {
        return channelId + ":" + accountId + ":" + conversationId;
    }

    public static SessionKey parse(final String raw) {
        Objects.requireNonNull(raw, "raw");
        final String[] parts = raw.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid session key: " + raw);
        }
        return new SessionKey(parts[0], parts[1], parts[2]);
    }
}
