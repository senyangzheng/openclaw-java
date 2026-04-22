package com.openclaw.sessions;

import java.util.Optional;

/**
 * Persistence contract for {@link Session}. M1 implementation is in-memory;
 * M2 brings a MyBatis-Plus backed implementation with Caffeine read-through cache.
 */
public interface SessionRepository {

    Optional<Session> find(SessionKey key);

    /**
     * Atomically load an existing session or create a new one under {@code key}.
     */
    Session loadOrCreate(SessionKey key);

    void save(Session session);

    void delete(SessionKey key);
}
