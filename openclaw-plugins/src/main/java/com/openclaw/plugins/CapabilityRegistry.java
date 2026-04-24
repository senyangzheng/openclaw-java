package com.openclaw.plugins;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.openclaw.plugin.CapabilityConflictException;
import com.openclaw.plugin.CapabilityType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Central store of named capabilities registered by plugins. Enforces per-type conflict policy:
 * <ul>
 *   <li>{@link CapabilityType.ConflictPolicy#HARD_REJECT}: second registration throws
 *       {@link CapabilityConflictException}; the loser's plugin onLoad aborts.</li>
 *   <li>{@link CapabilityType.ConflictPolicy#ALLOW_MULTIPLE}: both entries are retained in insertion order.</li>
 * </ul>
 *
 * <p>Entries are append-only within a single run; cleared only when the full plugin set is reloaded
 * (which currently only happens at process shutdown / restart — see M4.0 {@code GatewayReloadPlan}).
 *
 * <p>Thread-safety: all mutation happens on a single synchronized section keyed by {@link CapabilityType};
 * reads return copies so downstream consumers cannot mutate the internal state.
 */
public final class CapabilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(CapabilityRegistry.class);

    /** type → name → list of entries (singleton list for HARD_REJECT; can grow for ALLOW_MULTIPLE). */
    private final Map<CapabilityType, Map<String, List<Entry>>> byType = new LinkedHashMap<>();

    /** Register a new capability. See {@link com.openclaw.plugin.PluginContext#registerCapability}. */
    public synchronized <T> T register(final CapabilityType type,
                                       final String name,
                                       final String pluginId,
                                       final T handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("capability name must not be blank");
        }
        if (!StringUtils.hasText(pluginId)) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }

        final Map<String, List<Entry>> byName = byType.computeIfAbsent(type, t -> new LinkedHashMap<>());
        final List<Entry> existing = byName.get(name);

        if (existing != null && !existing.isEmpty()
                && type.policy() == CapabilityType.ConflictPolicy.HARD_REJECT) {
            final String ownerId = existing.get(0).pluginId();
            log.warn("plugin.capability.conflict type={} name={} incoming={} existing={}",
                    type, name, pluginId, ownerId);
            throw new CapabilityConflictException(type, name, pluginId, ownerId);
        }

        final Entry entry = new Entry(type, name, pluginId, handler);
        byName.computeIfAbsent(name, n -> new java.util.ArrayList<>()).add(entry);
        log.info("plugin.capability.registered type={} name={} plugin={} totalForName={}",
                type, name, pluginId, byName.get(name).size());
        return handler;
    }

    /** Look up the single (or first) handler for a capability. Returns empty when none registered. */
    public synchronized Optional<Object> find(final CapabilityType type, final String name) {
        final Map<String, List<Entry>> byName = byType.get(type);
        if (byName == null) {
            return Optional.empty();
        }
        final List<Entry> entries = byName.get(name);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(0).handler());
    }

    /** All entries for {@code type} in insertion order, grouped by name. Defensive copy. */
    public synchronized Map<String, List<Entry>> snapshot(final CapabilityType type) {
        final Map<String, List<Entry>> byName = byType.getOrDefault(type, Map.of());
        final Map<String, List<Entry>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Entry>> e : byName.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Full snapshot of every capability. Defensive deep copy. */
    public synchronized Map<CapabilityType, Map<String, List<Entry>>> snapshotAll() {
        final Map<CapabilityType, Map<String, List<Entry>>> copy = new LinkedHashMap<>();
        for (CapabilityType t : byType.keySet()) {
            copy.put(t, snapshot(t));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Drop every capability owned by {@code pluginId} (used during unload). Idempotent. */
    public synchronized void unregisterPlugin(final String pluginId) {
        if (!StringUtils.hasText(pluginId)) {
            return;
        }
        for (Map<String, List<Entry>> byName : byType.values()) {
            for (Map.Entry<String, List<Entry>> e : byName.entrySet()) {
                e.getValue().removeIf(entry -> pluginId.equals(entry.pluginId()));
            }
            byName.values().removeIf(List::isEmpty);
        }
    }

    /** Single registered capability entry. */
    public record Entry(CapabilityType type, String name, String pluginId, Object handler) {
        public Entry {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(handler, "handler");
        }
    }
}
