package com.openclaw.lanes;

import com.openclaw.common.util.Strings;

/**
 * Lane-name resolution rules.
 * <p>
 * Mirrors openclaw TS {@code src/agents/pi-embedded-runner/lanes.ts} ·
 * {@code resolveSessionLane / resolveGlobalLane}.
 *
 * <p>{@link #resolveSessionLane(String)} is idempotent w.r.t. {@code "session:"} prefix; empty key falls back to
 * {@link CommandLane#MAIN}. {@link #resolveGlobalLane(String)} empty falls back to {@link CommandLane#MAIN}.
 */
public final class LaneNames {

    public static final String SESSION_PREFIX = "session:";
    public static final String AUTH_PROBE_PREFIX = "auth-probe:";
    public static final String SESSION_PROBE_PREFIX = "session:probe-";

    private LaneNames() {
    }

    /**
     * Resolve a session-lane name. Empty / blank input falls back to {@link CommandLane#MAIN}.
     * <p>
     * Idempotent: if the trimmed input already starts with {@code "session:"} the input is returned as-is,
     * otherwise prefixed.
     */
    public static String resolveSessionLane(final String key) {
        final String cleaned = Strings.defaultIfBlank(key, CommandLane.MAIN.laneName()).trim();
        if (cleaned.startsWith(SESSION_PREFIX)) {
            return cleaned;
        }
        return SESSION_PREFIX + cleaned;
    }

    /**
     * Resolve a global-lane name. Empty / blank input falls back to {@link CommandLane#MAIN}.
     */
    public static String resolveGlobalLane(final String lane) {
        final String cleaned = lane == null ? "" : lane.trim();
        return cleaned.isEmpty() ? CommandLane.MAIN.laneName() : cleaned;
    }

    /**
     * Probe lanes whose task failures must be silenced (no error log).
     * <p>
     * Probe failures are expected (auth probing / session probing) and would otherwise generate misleading noise.
     */
    public static boolean isProbeLane(final String lane) {
        return lane != null && (lane.startsWith(AUTH_PROBE_PREFIX) || lane.startsWith(SESSION_PROBE_PREFIX));
    }
}
