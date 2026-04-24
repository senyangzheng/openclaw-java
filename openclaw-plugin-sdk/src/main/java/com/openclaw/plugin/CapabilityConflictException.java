package com.openclaw.plugin;

/**
 * Thrown when a plugin attempts to register a named capability that conflicts with an existing one and the
 * capability type's {@link CapabilityType#policy()} is {@link CapabilityType.ConflictPolicy#HARD_REJECT}.
 *
 * <p>The loader catches this, records a diagnostic entry for the offending plugin, and skips the rest of its
 * {@code onLoad} — without taking down the whole runtime.
 */
public class CapabilityConflictException extends PluginLoadException {

    private static final long serialVersionUID = 1L;

    private final CapabilityType type;
    private final String name;
    private final String existingPluginId;

    public CapabilityConflictException(final CapabilityType type,
                                       final String name,
                                       final String incomingPluginId,
                                       final String existingPluginId) {
        super(incomingPluginId, formatMessage(type, name, existingPluginId));
        this.type = type;
        this.name = name;
        this.existingPluginId = existingPluginId;
    }

    public CapabilityType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String existingPluginId() {
        return existingPluginId;
    }

    private static String formatMessage(final CapabilityType type,
                                        final String name,
                                        final String existingPluginId) {
        return "capability conflict: " + type + "[" + name + "] already registered by plugin '"
                + existingPluginId + "'";
    }
}
