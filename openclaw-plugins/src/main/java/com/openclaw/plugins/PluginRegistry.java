package com.openclaw.plugins;

import com.openclaw.plugin.OpenClawPlugin;
import com.openclaw.plugin.PluginDescriptor;

import java.util.List;
import java.util.Optional;

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
}
