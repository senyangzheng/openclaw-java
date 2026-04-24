package com.openclaw.plugin;

import java.time.Instant;

/**
 * Immutable runtime snapshot of a loaded plugin. Returned by the registry for
 * list / admin endpoints — never used as a configuration source.
 *
 * @param id          {@link OpenClawPlugin#id()}
 * @param version     {@link OpenClawPlugin#version()}
 * @param description {@link OpenClawPlugin#description()}
 * @param order       {@link OpenClawPlugin#order()}
 * @param className   FQN of the implementation class — useful for diagnostics
 *                    (which jar supplied this plugin)
 * @param source      where this plugin was discovered from (see {@link PluginSource})
 * @param loadedAt    Wall-clock moment at which {@link OpenClawPlugin#onLoad} completed
 */
public record PluginDescriptor(
    String id,
    String version,
    String description,
    int order,
    String className,
    PluginSource source,
    Instant loadedAt
) {

    public PluginDescriptor {
        source = source == null ? PluginSource.BUNDLED : source;
    }
}
