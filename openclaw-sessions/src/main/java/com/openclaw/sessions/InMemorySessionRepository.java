package com.openclaw.sessions;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local session store. Data is lost on restart — acceptable for M1 only.
 * Replaced by MyBatis-Plus implementation in M2.
 */
public class InMemorySessionRepository implements SessionRepository {

    private final ConcurrentMap<String, Session> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Session> find(final SessionKey key) {
        return Optional.ofNullable(store.get(key.asString()));
    }

    @Override
    public Session loadOrCreate(final SessionKey key) {
        return store.computeIfAbsent(key.asString(), k -> new Session(key));
    }

    @Override
    public void save(final Session session) {
        store.put(session.key().asString(), session);
    }

    @Override
    public void delete(final SessionKey key) {
        store.remove(key.asString());
    }

    public int size() {
        return store.size();
    }
}
