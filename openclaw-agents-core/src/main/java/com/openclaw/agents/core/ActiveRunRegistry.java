package com.openclaw.agents.core;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.sessions.SessionKey;

/**
 * Per-session mutex registry for active agent runs.
 *
 * <p>Mirrors openclaw-ts {@code src/agents/pi-embedded-runner/active-run-registry.ts}. Enforces the invariant
 * "at most one active run per {@code SessionKey}" — required by session-lane semantics (the outer session
 * lane has capacity 1, but that only guards serialization; this registry adds an explicit named slot so
 * callers can reject attempts instead of waiting).
 *
 * <p><b>Precise contract</b> (see §12 #4):
 * <ul>
 *   <li>{@link #tryRegister(AgentRunHandle)} returns {@code false} when another run is active on the same
 *       session, so callers can reject with {@link AgentErrorCode#ACTIVE_RUN_CONFLICT}; atomic via
 *       {@link ConcurrentHashMap#putIfAbsent}</li>
 *   <li>{@link #registerOrThrow(AgentRunHandle)} is the same but throws on conflict; use when the caller is
 *       certain no other run should exist</li>
 *   <li>{@link #clearIfMatches(SessionKey, AgentRunHandle)} removes the slot only when the stored handle
 *       matches by identity ({@code ==}); prevents stale handles from clearing a successor's slot when a
 *       cleanup runs after the next run has already started</li>
 *   <li>{@link #unregister(SessionKey)} forces removal regardless of handle; use during
 *       {@code resetAllLanes} emergencies only</li>
 * </ul>
 */
public final class ActiveRunRegistry {

    private final ConcurrentHashMap<SessionKey, AgentRunHandle> bySession = new ConcurrentHashMap<>();

    public boolean tryRegister(final AgentRunHandle handle) {
        Objects.requireNonNull(handle, "handle");
        final AgentRunHandle previous = bySession.putIfAbsent(handle.sessionKey(), handle);
        return previous == null;
    }

    public void registerOrThrow(final AgentRunHandle handle) {
        if (!tryRegister(handle)) {
            throw new OpenClawException(AgentErrorCode.ACTIVE_RUN_CONFLICT,
                    "Session already has an active run: " + handle.sessionKey().asString());
        }
    }

    public Optional<AgentRunHandle> current(final SessionKey sessionKey) {
        return Optional.ofNullable(bySession.get(sessionKey));
    }

    /**
     * Remove the slot iff the stored handle is {@code ==} the given handle. Matches openclaw-ts
     * {@code clearIfMatches(prevHandle)} — using identity prevents accidental removal when a later run has
     * already taken the slot.
     *
     * @return {@code true} when the slot was removed, {@code false} when the stored handle differs (or there
     *         is no stored handle)
     */
    public boolean clearIfMatches(final SessionKey sessionKey, final AgentRunHandle expected) {
        Objects.requireNonNull(sessionKey, "sessionKey");
        Objects.requireNonNull(expected, "expected");
        return bySession.remove(sessionKey, expected);
    }

    /** Force-remove a slot (use only for resetAllLanes / test cleanup). */
    public void unregister(final SessionKey sessionKey) {
        bySession.remove(Objects.requireNonNull(sessionKey, "sessionKey"));
    }

    public int size() {
        return bySession.size();
    }

    public void clear() {
        bySession.clear();
    }
}
