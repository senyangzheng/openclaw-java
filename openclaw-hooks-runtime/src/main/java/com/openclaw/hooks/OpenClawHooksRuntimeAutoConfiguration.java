package com.openclaw.hooks;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for {@link HookRunner}.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@link HookDiagnostics} — append-only diagnostic sink</li>
 *   <li>{@link HookRunner} — singleton runner backed by a virtual-thread executor (unless a user-provided
 *       {@link Executor} named {@code hookRunnerExecutor} is present)</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(HookRuntimeProperties.class)
public class OpenClawHooksRuntimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HookDiagnostics hookDiagnostics() {
        return new HookDiagnostics();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public HookRunner hookRunner(final HookRuntimeProperties props,
                                 final HookDiagnostics diagnostics,
                                 final ObjectProvider<Executor> userExecutor) {
        final Executor executor = userExecutor.getIfAvailable(Executors::newVirtualThreadPerTaskExecutor);
        return new HookRunner(executor, diagnostics, props.isCatchErrors());
    }
}
