package com.openclaw.agents.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for {@code openclaw-agents-core}.
 *
 * <p>All keys are rooted at {@code openclaw.agents}.
 *
 * @param enabled           master on/off switch (default {@code true}); set {@code false} to exclude the whole
 *                          agent runtime — useful when running the app as a plain auto-reply service
 * @param mutex             mutex policy for session-level active-run guard
 */
@ConfigurationProperties(prefix = AgentsCoreProperties.PREFIX)
public record AgentsCoreProperties(boolean enabled, Mutex mutex) {

    public static final String PREFIX = "openclaw.agents";

    public AgentsCoreProperties {
        if (mutex == null) {
            mutex = Mutex.DEFAULT;
        }
    }

    /**
     * @param rejectOnConflict when {@code true} (default), a second concurrent run on the same session is
     *                         rejected immediately; when {@code false}, callers rely purely on session-lane
     *                         serialization (not recommended — loses observability of duplicate submits)
     */
    public record Mutex(boolean rejectOnConflict) {
        public static final Mutex DEFAULT = new Mutex(true);
    }
}
