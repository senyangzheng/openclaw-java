package com.openclaw.config;

import com.openclaw.config.hotreload.ConfigReloadPublisher;
import com.openclaw.config.hotreload.ConfigWatcher;
import com.openclaw.config.hotreload.HotReloadProperties;
import com.openclaw.config.hotreload.HotReloadable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Registers {@link OpenClawProperties} and, optionally, the hot-reload watcher.
 *
 * <p>The watcher is gated behind {@code openclaw.config.hot-reload.enabled=true};
 * when off, zero file descriptors or threads are consumed.
 */
@AutoConfiguration
@EnableConfigurationProperties({ OpenClawProperties.class, HotReloadProperties.class })
public class OpenClawConfigAutoConfiguration {

    /**
     * Isolated inner config so the {@link ConfigWatcher} beans don't appear in
     * the context at all when hot reload is off. Without the nested
     * {@link ConditionalOnProperty} the {@link HotReloadProperties} bean would
     * still be registered (needed for admin endpoints to read the flag).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "openclaw.config.hot-reload", name = "enabled", havingValue = "true")
    static class HotReloadConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ConfigReloadPublisher configReloadPublisher(final ObjectProvider<HotReloadable> listeners,
                                                    final ApplicationEventPublisher eventPublisher) {
            final List<HotReloadable> ordered = listeners.orderedStream().toList();
            return new ConfigReloadPublisher(ordered, eventPublisher);
        }

        @Bean(destroyMethod = "destroy")
        @ConditionalOnMissingBean
        ConfigWatcher configWatcher(final HotReloadProperties properties,
                                    final ConfigReloadPublisher publisher) {
            return new ConfigWatcher(properties, publisher);
        }
    }
}
