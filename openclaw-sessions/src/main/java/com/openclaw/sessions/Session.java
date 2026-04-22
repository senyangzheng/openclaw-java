package com.openclaw.sessions;

import com.openclaw.providers.api.ChatMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An in-memory conversational session. Used both as the in-process representation
 * and the storage record. M2's {@code JdbcSessionRepository} rehydrates instances
 * via {@link #Session(SessionKey, Instant, Instant, List)}.
 */
public final class Session {

    private final SessionKey key;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<ChatMessage> messages = new ArrayList<>();

    public Session(final SessionKey key) {
        this.key = Objects.requireNonNull(key, "key");
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Restores a session from persistent storage. Skips the {@code append} path
     * so timestamps and history stay byte-for-byte identical to what was saved.
     */
    public Session(final SessionKey key, final Instant createdAt, final Instant updatedAt,
                   final List<ChatMessage> history) {
        this.key = Objects.requireNonNull(key, "key");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (history != null) {
            this.messages.addAll(history);
        }
    }

    public SessionKey key() {
        return key;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<ChatMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    public synchronized void append(final ChatMessage message) {
        Objects.requireNonNull(message, "message");
        messages.add(message);
        updatedAt = Instant.now();
    }
}
