package com.openclaw.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * Try-with-resources helper for scoped MDC entries.
 * <pre>{@code
 * try (var ignored = MdcScope.of(MdcKeys.REQUEST_ID, requestId)
 *                             .with(MdcKeys.SESSION_ID, sessionId)) {
 *     log.info("handling");
 * }
 * }</pre>
 * Restores prior MDC values on close (including removing entries absent before the scope).
 */
public final class MdcScope implements AutoCloseable {

    private final Map<String, String> previous = new HashMap<>();

    private MdcScope() {
    }

    public static MdcScope of(final String key, final String value) {
        return new MdcScope().with(key, value);
    }

    public MdcScope with(final String key, final String value) {
        if (key == null || value == null) {
            return this;
        }
        if (!previous.containsKey(key)) {
            previous.put(key, MDC.get(key));
        }
        MDC.put(key, value);
        return this;
    }

    @Override
    public void close() {
        for (Map.Entry<String, String> entry : previous.entrySet()) {
            if (entry.getValue() == null) {
                MDC.remove(entry.getKey());
            } else {
                MDC.put(entry.getKey(), entry.getValue());
            }
        }
        previous.clear();
    }
}
