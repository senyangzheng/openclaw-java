package com.openclaw.hooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Append-only diagnostic log of hook-level incidents (registration conflicts, handler errors).
 * Thread-safe through {@code synchronized(entries)}.
 */
public final class HookDiagnostics {

    public record Entry(long timestampMs, String severity, String hookName, String handlerId, String message) {
        public Entry {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(hookName, "hookName");
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void record(final String severity, final String hookName, final String handlerId, final String message) {
        final Entry entry = new Entry(System.currentTimeMillis(),
                severity == null ? "INFO" : severity,
                hookName == null ? "?" : hookName,
                handlerId == null ? "?" : handlerId,
                message == null ? "" : message);
        synchronized (entries) {
            entries.add(entry);
        }
    }

    public List<Entry> snapshot() {
        synchronized (entries) {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }
}
