package com.openclaw.plugins;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.openclaw.plugin.CapabilityType;
import com.openclaw.plugin.OpenClawPlugin;
import com.openclaw.plugin.PluginDescriptor;

/**
 * Query API for the currently loaded plugins. Exposed as a Spring bean — consumers
 * such as admin endpoints or gateway method groups can list / inspect plugins
 * without touching the loader.
 */
public interface PluginRegistry {

    /** Snapshot of every successfully loaded plugin, sorted by {@code order}. */
    List<PluginDescriptor> descriptors();

    /** Look up a plugin by id. */
    Optional<PluginDescriptor> find(String id);

    /** Underlying {@link OpenClawPlugin} instance — exposed for advanced
     * integration (e.g. gateway reload triggering {@link OpenClawPlugin#onUnload}).
     * Most callers should stick to {@link #descriptors()}. */
    Optional<OpenClawPlugin> plugin(String id);

    /**
     * Lookup a registered capability handler (gateway method / HTTP route / CLI command / tool / hook).
     * Returns the first registration for HARD_REJECT types; for HOOK (ALLOW_MULTIPLE) returns the first
     * registered — prefer {@link #capabilities(CapabilityType)} to see the full list.
     */
    Optional<Object> capability(CapabilityType type, String name);

    /** All capabilities of a given type, grouped by name. Iteration follows registration order. */
    Map<String, List<CapabilityRegistry.Entry>> capabilities(CapabilityType type);

    /** Full diagnostics log (loader errors, conflicts, etc.) — stable, append-only. */
    List<PluginDiagnostics.Entry> diagnostics();
}
