package com.openclaw.plugin;

/**
 * Thrown by the loader when a plugin fails to instantiate, to validate its
 * metadata, or when {@link OpenClawPlugin#onLoad(PluginContext)} raises.
 *
 * <p>The runtime catches this, logs with plugin id + cause, and continues
 * loading the remaining plugins. A single broken plugin never crashes the
 * whole application.
 */
public class PluginLoadException extends RuntimeException {

    private final String pluginId;

    public PluginLoadException(final String pluginId, final String message) {
        super(message);
        this.pluginId = pluginId;
    }

    public PluginLoadException(final String pluginId, final String message, final Throwable cause) {
        super(message, cause);
        this.pluginId = pluginId;
    }

    public String pluginId() {
        return pluginId;
    }
}
