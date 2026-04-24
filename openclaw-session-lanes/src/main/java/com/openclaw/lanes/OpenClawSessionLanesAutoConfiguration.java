package com.openclaw.lanes;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the session-lane runtime.
 *
 * <p>Binds {@link SessionLaneProperties} to {@code openclaw.lanes.*} and exposes a singleton
 * {@link SessionLaneCoordinator} bean. The coordinator is closed on context shutdown.
 */
@AutoConfiguration
@EnableConfigurationProperties(SessionLaneProperties.class)
public class OpenClawSessionLanesAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public SessionLaneCoordinator sessionLaneCoordinator(final SessionLaneProperties props) {
        final SessionLaneCoordinator coordinator = new SessionLaneCoordinator();
        coordinator.applyGlobalConcurrency(props.toGlobalConcurrency());
        return coordinator;
    }
}
