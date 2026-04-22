package com.openclaw.plugins;

import com.openclaw.plugin.PluginContext;
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
 */
final class DefaultPluginContext implements PluginContext {

    private final String pluginId;
    private final ConfigurableApplicationContext applicationContext;

    DefaultPluginContext(final String pluginId, final ConfigurableApplicationContext applicationContext) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
    }

    @Override
    public String pluginId() {
        return pluginId;
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
    public void publishEvent(final Object event) {
        applicationContext.publishEvent(event);
    }

    @Override
    public ConfigurableListableBeanFactory beanFactory() {
        return applicationContext.getBeanFactory();
    }
}
