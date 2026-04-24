package com.openclaw.lanes;

/**
 * Immutable caps for global {@link CommandLane} categories.
 *
 * <p>Defaults: {@code cron=1}, {@code main=2}, {@code subagent=2}; all must be &gt;= 1.
 */
public record GlobalLaneConcurrency(int cron, int main, int subagent) {

    public static final int DEFAULT_CRON = 1;
    public static final int DEFAULT_MAIN = 2;
    public static final int DEFAULT_SUBAGENT = 2;

    public static final GlobalLaneConcurrency DEFAULTS =
            new GlobalLaneConcurrency(DEFAULT_CRON, DEFAULT_MAIN, DEFAULT_SUBAGENT);

    public GlobalLaneConcurrency {
        requirePositive(cron, "cron");
        requirePositive(main, "main");
        requirePositive(subagent, "subagent");
    }

    private static void requirePositive(final int value, final String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be >= 1, got " + value);
        }
    }
}
