package com.openclaw.providers.registry;

import com.openclaw.autoreply.OpenClawAutoReplyAutoConfiguration;
import com.openclaw.providers.api.CooldownPolicy;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.secrets.vault.AuthProfileVault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.util.List;

/**
 * Wires the multi-provider registry behind an {@code @Primary}
 * {@link CompositeProviderClient}. Runs after the individual provider autoconfigs
 * (by class name — no compile dep on them) and before
 * {@link OpenClawAutoReplyAutoConfiguration} so the pipeline sees the composite.
 *
 * <p>No-op when there are zero {@link ProviderClient} beans — the pipeline still
 * falls back to {@link com.openclaw.providers.api.mock.EchoMockProviderClient}.
 */
@AutoConfiguration(before = OpenClawAutoReplyAutoConfiguration.class)
@AutoConfigureAfter(name = {
    "com.openclaw.providers.qwen.OpenClawProvidersQwenAutoConfiguration",
    "com.openclaw.providers.google.OpenClawProvidersGoogleAutoConfiguration"
})
@ConditionalOnBean(ProviderClient.class)
@EnableConfigurationProperties(ProviderRegistryProperties.class)
public class OpenClawProvidersRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawProvidersRegistryAutoConfiguration.class);

    /**
     * Collects concrete {@link ProviderClient} beans while explicitly filtering out
     * {@link CompositeProviderClient} — the composite is itself a {@code ProviderClient}
     * and would otherwise be pulled into the registry's own member list, creating a
     * self-referential chain. Pairing this filter with the lazy {@code ObjectProvider}
     * lookup inside {@link CompositeProviderClient} lets both beans coexist without
     * relying on {@code spring.main.allow-circular-references}.
     */
    @Bean
    @ConditionalOnMissingBean(ProviderRegistry.class)
    public ProviderRegistry providerRegistry(final List<ProviderClient> providerClients,
                                              final ProviderRegistryProperties properties,
                                              final ObjectProvider<AuthProfileVault> vaultProvider) {
        final List<ProviderClient> realProviders = providerClients.stream()
            .filter(p -> !(p instanceof CompositeProviderClient))
            .toList();
        final CooldownPolicy policy = new CooldownPolicy(
            properties.getCooldown().getInitialDelay(),
            properties.getCooldown().getMaxDelay(),
            properties.getCooldown().getMultiplier());
        // Vault is optional — when absent (e.g. no openclaw-secrets wired) the
        // registry's authProfile(..) lookups return empty and consumers fall
        // back to their @ConfigurationProperties api-key.
        final AuthProfileVault vault = vaultProvider.getIfAvailable();
        log.info("providers.registry.bootstrapping providers={} preferredOrder={} vault={}",
            realProviders.stream().map(ProviderClient::providerId).toList(),
            properties.getOrder(),
            vault != null ? vault.source() : "none");
        return new DefaultProviderRegistry(realProviders, properties.getOrder(), policy,
            Clock.systemUTC(), vault);
    }

    /**
     * Registered as {@link Primary} so consumers autowiring {@code ProviderClient} by
     * type pick up this composite rather than any concrete provider bean.
     *
     * <p>Takes {@link ObjectProvider} instead of a direct {@link ProviderRegistry}:
     * registry creation depends on {@code List<ProviderClient>} which — without the
     * filter above — would include the composite, i.e. create a cycle. By resolving
     * the registry lazily we let Spring finish wiring both beans first, then the
     * lookup at call time always hits a fully-initialised instance.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(CompositeProviderClient.class)
    public ProviderClient registryProviderClient(final ObjectProvider<ProviderRegistry> registryProvider) {
        return new CompositeProviderClient(registryProvider::getObject);
    }
}
