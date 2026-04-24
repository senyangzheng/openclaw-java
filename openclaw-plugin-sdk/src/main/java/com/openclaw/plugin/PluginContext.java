package com.openclaw.plugin;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import java.util.function.Supplier;

/**
 * Runtime facade handed to each plugin in {@link OpenClawPlugin#onLoad}.
 *
 * <p>Keeps the plugin SPI decoupled from Spring Boot internals: a plugin can
 * register a bean, read the {@link Environment}, or publish events through
 * this interface without importing any {@code spring-boot-*} symbol.
 *
 * <p>The implementation in {@code openclaw-plugins} wraps the live
 * {@link org.springframework.context.ConfigurableApplicationContext}; tests
 * may substitute a lightweight {@link ConfigurableListableBeanFactory} stub.
 *
 * <h2>Named capability registration (M3)</h2>
 * Plugins register addressable capabilities (gateway methods, HTTP routes, CLI commands, tools, hooks) via
 * {@link #registerCapability(CapabilityType, String, Object)}. The runtime enforces
 * {@link CapabilityType#policy() conflict governance} centrally and publishes the full registry through
 * {@code PluginRegistry#diagnostics()} so operators see which plugin owns which name.
 */
public interface PluginContext {

    /** Stable id of the plugin this context was scoped for. Useful when a
     * plugin registers multiple beans and wants to prefix names uniformly. */
    String pluginId();

    /** Source of this plugin (BUNDLED / GLOBAL / WORKSPACE / CONFIG). Plugins can use the source to gate
     * behaviour (e.g. skip expensive eager setup when source is {@link PluginSource#BUNDLED}). */
    PluginSource source();

    /** Application {@link Environment}. Plugins read their own properties
     * here, prefixed by {@code openclaw.plugins.<id>.}. */
    Environment environment();

    /** Register a singleton bean with the host {@link ConfigurableListableBeanFactory}.
     * {@code beanName} is automatically prefixed with {@code plugin.<id>.} to
     * avoid collisions across plugins. */
    <T> T registerSingleton(String beanName, Supplier<T> factory);

    /**
     * Register a named capability (gateway method / HTTP route / CLI command / tool / hook).
     *
     * <p>Conflict rules are dictated by {@link CapabilityType#policy()}:
     * <ul>
     *   <li>{@link CapabilityType.ConflictPolicy#HARD_REJECT}: throws
     *       {@link CapabilityConflictException} if {@code (type, name)} is already registered — the loader
     *       catches it, records a diagnostic, and aborts this plugin's {@code onLoad}.</li>
     *   <li>{@link CapabilityType.ConflictPolicy#ALLOW_MULTIPLE}: returns the handler as-is;
     *       downstream (e.g. {@code HookRunner}) orders / fans out multi-registered entries.</li>
     * </ul>
     *
     * @param type    capability kind (namespace for conflict detection)
     * @param name    capability name within that namespace (e.g. {@code "chat.send"}, {@code "/plugin/hello"})
     * @param handler the capability implementation; kept opaque here to avoid forcing the SDK to depend on
     *                gateway / tool / hook modules
     * @return the same {@code handler} (for fluent chaining)
     * @throws CapabilityConflictException when {@code type} is HARD_REJECT and {@code (type, name)} is taken
     */
    <T> T registerCapability(CapabilityType type, String name, T handler);

    /** Publish an {@link org.springframework.context.ApplicationEvent}-style
     * payload. Delegates to the host context's {@code publishEvent}. */
    void publishEvent(Object event);

    /** Direct access to the bean factory — escape hatch for advanced plugins
     * (e.g. those that need to post-process existing beans). Prefer
     * {@link #registerSingleton(String, Supplier)} whenever possible. */
    ConfigurableListableBeanFactory beanFactory();
}
