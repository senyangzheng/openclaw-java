package com.openclaw.agents.core;

import com.openclaw.hooks.HookRunner;
import com.openclaw.lanes.SessionLaneCoordinator;
import com.openclaw.providers.registry.ProviderDispatcher;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the agent runtime skeleton ({@link PiAgentRunner} + {@link AttemptExecutor} +
 * {@link ActiveRunRegistry}). Triggered by the presence of {@link SessionLaneCoordinator} and
 * {@link HookRunner} on the classpath.
 *
 * <p>Set {@code openclaw.agents.enabled=false} to exclude the runtime (useful for tests or for running the app
 * as a plain auto-reply service without M3+).
 */
@AutoConfiguration
@ConditionalOnClass({SessionLaneCoordinator.class, HookRunner.class})
@ConditionalOnProperty(prefix = AgentsCoreProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgentsCoreProperties.class)
public class OpenClawAgentsCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ActiveRunRegistry activeRunRegistry() {
        return new ActiveRunRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public AttemptExecutor attemptExecutor(final ProviderDispatcher dispatcher, final HookRunner hookRunner) {
        return new AttemptExecutor(dispatcher, hookRunner);
    }

    @Bean
    @ConditionalOnMissingBean
    public PiAgentRunner piAgentRunner(final SessionLaneCoordinator lanes,
                                       final ActiveRunRegistry activeRunRegistry,
                                       final AttemptExecutor attemptExecutor) {
        return new PiAgentRunner(lanes, activeRunRegistry, attemptExecutor);
    }
}
