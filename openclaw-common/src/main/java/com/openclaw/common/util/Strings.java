package com.openclaw.common.util;

/**
 * Minimal string helpers. Kept intentionally tiny; prefer JDK 21 {@code String} APIs.
 */
public final class Strings {

    private Strings() {
    }

    public static boolean isBlank(final String s) {
        return s == null || s.isBlank();
    }

    public static boolean isNotBlank(final String s) {
        return !isBlank(s);
    }

    public static String defaultIfBlank(final String s, final String fallback) {
        return isBlank(s) ? fallback : s;
    }

    /** Null-safe trim; returns {@code null} for {@code null} input. */
    public static String trimToNull(final String s) {
        if (s == null) {
            return null;
        }
        final String trimmed = s.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
