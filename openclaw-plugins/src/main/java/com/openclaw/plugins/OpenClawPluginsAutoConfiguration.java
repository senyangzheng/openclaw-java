package com.openclaw.plugins;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Wires the plugin loader + registry + capability registry + diagnostics. Activated unconditionally
 * (always present when {@code openclaw-plugins} is on the classpath); the
 * {@link PluginProperties#isEnabled()} switch gates actual discovery at runtime.
 */
@AutoConfiguration
@EnableConfigurationProperties(PluginProperties.class)
public class OpenClawPluginsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CapabilityRegistry pluginCapabilityRegistry() {
        return new CapabilityRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginDiagnostics pluginDiagnostics() {
        return new PluginDiagnostics();
    }

    @Bean
    @ConditionalOnMissingBean(PluginLoader.class)
    public PluginLoader pluginLoader(final ConfigurableApplicationContext applicationContext,
                                     final PluginProperties properties,
                                     final CapabilityRegistry capabilityRegistry,
                                     final PluginDiagnostics diagnostics) {
        return new PluginLoader(applicationContext, properties, capabilityRegistry, diagnostics);
    }

    @Bean
    @ConditionalOnMissingBean(PluginRegistry.class)
    public PluginRegistry pluginRegistry(final PluginLoader pluginLoader) {
        return pluginLoader;
    }
}
