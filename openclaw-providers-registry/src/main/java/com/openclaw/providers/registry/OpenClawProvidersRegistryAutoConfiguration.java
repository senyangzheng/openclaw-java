package com.openclaw.providers.registry;

import java.time.Clock;
import java.util.List;

import com.openclaw.providers.api.CooldownPolicy;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.providers.api.mock.EchoMockProviderClient;
import com.openclaw.secrets.vault.AuthProfileVault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the multi-provider registry and the {@link ProviderDispatcher} facade in front of it. Runs after
 * the individual provider autoconfigs (referenced by class name — no compile-time dep on them) and before
 * {@code OpenClawAutoReplyAutoConfiguration} so the pipeline sees the dispatcher.
 *
 * <h2>Mock fallback</h2>
 * When no real {@link ProviderClient} beans are present, the registry is still bootstrapped with an
 * {@link EchoMockProviderClient} as its only candidate so downstream code (AutoReplyPipeline,
 * AttemptExecutor) always has a dispatcher available. This replaces the old auto-reply-side
 * {@code @ConditionalOnMissingBean(ProviderClient.class)} fallback — the mock now lives next to the
 * dispatcher, not next to the pipeline.
 *
 * <h2>M3 / A4 note</h2>
 * Before this milestone we registered a {@code @Primary CompositeProviderClient} that implemented
 * {@code ProviderClient}. That bean was removed because wrapping a multi-supplier router in the
 * single-supplier SPI caused the registry autoconfig to filter itself out of its own member list and forced
 * upstream code to pretend the dispatcher was a plain provider. Callers now depend on
 * {@link ProviderDispatcher} directly.
 */
@AutoConfiguration(beforeName = "com.openclaw.autoreply.OpenClawAutoReplyAutoConfiguration")
@AutoConfigureAfter(name = {
        "com.openclaw.providers.qwen.OpenClawProvidersQwenAutoConfiguration",
        "com.openclaw.providers.google.OpenClawProvidersGoogleAutoConfiguration"
})
@EnableConfigurationProperties(ProviderRegistryProperties.class)
public class OpenClawProvidersRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawProvidersRegistryAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ProviderRegistry.class)
    public ProviderRegistry providerRegistry(final ObjectProvider<ProviderClient> providerClients,
                                             final ProviderRegistryProperties properties,
                                             final ObjectProvider<AuthProfileVault> vaultProvider) {
        List<ProviderClient> real = providerClients.orderedStream().toList();
        final boolean usingMockFallback = real.isEmpty();
        if (usingMockFallback) {
            log.warn("providers.registry.fallback using EchoMockProviderClient "
                    + "— no real ProviderClient bean registered. Set openclaw.providers.qwen.enabled=true "
                    + "+ DASHSCOPE_API_KEY to use Qwen, or openclaw.providers.google.enabled=true "
                    + "+ GOOGLE_API_KEY for Gemini.");
            real = List.of(new EchoMockProviderClient());
        }
        final CooldownPolicy policy = new CooldownPolicy(
                properties.getCooldown().getInitialDelay(),
                properties.getCooldown().getMaxDelay(),
                properties.getCooldown().getMultiplier());
        final AuthProfileVault vault = vaultProvider.getIfAvailable();
        log.info("providers.registry.bootstrapping providers={} preferredOrder={} vault={} mockFallback={}",
                real.stream().map(ProviderClient::providerId).toList(),
                properties.getOrder(),
                vault != null ? vault.source() : "none",
                usingMockFallback);
        return new DefaultProviderRegistry(real, properties.getOrder(), policy,
                Clock.systemUTC(), vault);
    }

    @Bean
    @ConditionalOnMissingBean(ProviderDispatcher.class)
    public ProviderDispatcher providerDispatcher(final ProviderRegistry registry) {
        return new FailoverProviderDispatcher(registry);
    }
}
