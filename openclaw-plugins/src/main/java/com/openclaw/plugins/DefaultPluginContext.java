package com.openclaw.plugins;

import com.openclaw.plugin.CapabilityType;
import com.openclaw.plugin.PluginContext;
import com.openclaw.plugin.PluginSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Default {@link PluginContext} implementation. Wraps the live
 * {@link ConfigurableApplicationContext} and prefixes every registered bean
 * name with {@code plugin.<id>.} so two plugins cannot clash on bean names.
 *
 * <p><b>Lifecycle contract</b>: {@link #registerSingleton(String, Supplier)} MUST
 * only be called from {@link com.openclaw.plugin.OpenClawPlugin#onLoad} —
 * after the host context has finished refresh. Registering after the context
 * is closed throws {@link IllegalStateException}.
 *
 * <p><b>Capability routing (M3)</b>: {@link #registerCapability(CapabilityType, String, Object)} delegates
 * to the shared {@link CapabilityRegistry}, which enforces per-type conflict policy and records diagnostics.
 */
final class DefaultPluginContext implements PluginContext {

    private final String pluginId;
    private final PluginSource source;
    private final ConfigurableApplicationContext applicationContext;
    private final CapabilityRegistry capabilityRegistry;

    DefaultPluginContext(final String pluginId,
                         final PluginSource source,
                         final ConfigurableApplicationContext applicationContext,
                         final CapabilityRegistry capabilityRegistry) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.source = source == null ? PluginSource.BUNDLED : source;
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public PluginSource source() {
        return source;
    }

    @Override
    public Environment environment() {
        return applicationContext.getEnvironment();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T registerSingleton(final String beanName, final Supplier<T> factory) {
        Objects.requireNonNull(beanName, "beanName");
        Objects.requireNonNull(factory, "factory");
        if (!applicationContext.isActive()) {
            throw new IllegalStateException(
                "cannot register plugin bean after context shutdown: plugin=" + pluginId);
        }
        final String qualified = "plugin." + pluginId + "." + beanName;
        final T instance = factory.get();
        beanFactory().registerSingleton(qualified, instance);
        return (T) beanFactory().getSingleton(qualified);
    }

    @Override
    public <T> T registerCapability(final CapabilityType type, final String name, final T handler) {
        return capabilityRegistry.register(type, name, pluginId, handler);
    }

    @Override
    public void publishEvent(final Object event) {
        applicationContext.publishEvent(event);
    }

    @Override
    public ConfigurableListableBeanFactory beanFactory() {
        return applicationContext.getBeanFactory();
    }
}
