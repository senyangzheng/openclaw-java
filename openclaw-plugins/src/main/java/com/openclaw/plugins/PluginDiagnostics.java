package com.openclaw.plugins;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.openclaw.plugin.CapabilityType;

/**
 * Append-only collector of plugin governance events: loader errors, capability conflicts, duplicate ids,
 * configuration-schema issues, unload failures. Exposed on {@link PluginRegistry#diagnostics()} so admin /
 * actuator endpoints can surface "why was plugin X dropped?" without scraping logs.
 *
 * <p>Thread-safe via synchronized add/snapshot. Capped at {@link #MAX_ENTRIES} (oldest dropped) to avoid
 * unbounded growth when a misbehaving plugin restarts in a loop.
 */
public final class PluginDiagnostics {

    public static final int MAX_ENTRIES = 1024;

    private final List<Entry> entries = new ArrayList<>();

    public synchronized void record(final Entry entry) {
        Objects.requireNonNull(entry, "entry");
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    public synchronized List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized void clear() {
        entries.clear();
    }

    // -----------------------------------------------------------------------------------------------------
    // Factory helpers (kept terse so call-sites stay readable)
    // -----------------------------------------------------------------------------------------------------

    public static Entry loaderError(final String pluginId, final String className, final Throwable cause) {
        return new Entry(Kind.LOADER_ERROR, pluginId, null, null, className,
                cause.getClass().getSimpleName() + ": " + cause.getMessage(), Instant.now(), Map.of());
    }

    public static Entry duplicateId(final String pluginId, final String className) {
        return new Entry(Kind.DUPLICATE_ID, pluginId, null, null, className,
                "duplicate plugin id ignored (first winner keeps the slot)",
                Instant.now(), Map.of());
    }

    public static Entry capabilityConflict(final String incomingPluginId,
                                           final String existingPluginId,
                                           final CapabilityType type,
                                           final String name) {
        return new Entry(Kind.CAPABILITY_CONFLICT, incomingPluginId, type, name, null,
                "capability " + type + "[" + name + "] already owned by '" + existingPluginId + "'",
                Instant.now(),
                Map.of("existingPluginId", existingPluginId));
    }

    public static Entry configSchema(final String pluginId, final String keyPath, final String message) {
        return new Entry(Kind.CONFIG_SCHEMA, pluginId, null, null, null, message, Instant.now(),
                Map.of("keyPath", keyPath));
    }

    public static Entry unloadError(final String pluginId, final Throwable cause) {
        return new Entry(Kind.UNLOAD_ERROR, pluginId, null, null, null,
                cause.getClass().getSimpleName() + ": " + cause.getMessage(), Instant.now(), Map.of());
    }

    /** Event kind. */
    public enum Kind {
        /** {@code onLoad} threw. */
        LOADER_ERROR,
        /** Two plugins declared the same id on the classpath. */
        DUPLICATE_ID,
        /** Two plugins registered the same HARD_REJECT capability. */
        CAPABILITY_CONFLICT,
        /** Plugin-specific {@code openclaw.plugins.<id>.*} properties failed validation. */
        CONFIG_SCHEMA,
        /** {@code onUnload} threw. */
        UNLOAD_ERROR
    }

    /**
     * Single diagnostic event.
     *
     * @param kind            what happened
     * @param pluginId        offending plugin (may be blank if detected before id was known)
     * @param capabilityType  {@code null} unless {@link Kind#CAPABILITY_CONFLICT}
     * @param capabilityName  {@code null} unless {@link Kind#CAPABILITY_CONFLICT}
     * @param className       FQN of the implementation if known (for LOADER / DUPLICATE)
     * @param message         human-readable summary
     * @param recordedAt      wall clock
     * @param extras          additional structured data (never null)
     */
    public record Entry(Kind kind,
                        String pluginId,
                        CapabilityType capabilityType,
                        String capabilityName,
                        String className,
                        String message,
                        Instant recordedAt,
                        Map<String, Object> extras) {

        public Entry {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(recordedAt, "recordedAt");
            extras = extras == null ? Map.of() : Map.copyOf(extras);
        }
    }
}
