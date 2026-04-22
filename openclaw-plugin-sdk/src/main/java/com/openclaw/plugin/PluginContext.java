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
 */
public interface PluginContext {

    /** Stable id of the plugin this context was scoped for. Useful when a
     * plugin registers multiple beans and wants to prefix names uniformly. */
    String pluginId();

    /** Application {@link Environment}. Plugins read their own properties
     * here, prefixed by {@code openclaw.plugins.<id>.}. */
    Environment environment();

    /** Register a singleton bean with the host {@link ConfigurableListableBeanFactory}.
     * {@code beanName} is automatically prefixed with {@code plugin.<id>.} to
     * avoid collisions across plugins. */
    <T> T registerSingleton(String beanName, Supplier<T> factory);

    /** Publish an {@link org.springframework.context.ApplicationEvent}-style
     * payload. Delegates to the host context's {@code publishEvent}. */
    void publishEvent(Object event);

    /** Direct access to the bean factory — escape hatch for advanced plugins
     * (e.g. those that need to post-process existing beans). Prefer
     * {@link #registerSingleton(String, Supplier)} whenever possible. */
    ConfigurableListableBeanFactory beanFactory();
}
