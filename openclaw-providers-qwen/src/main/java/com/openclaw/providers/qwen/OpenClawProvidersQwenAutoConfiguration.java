package com.openclaw.providers.qwen;

import com.openclaw.autoreply.OpenClawAutoReplyAutoConfiguration;
import com.openclaw.providers.api.AuthProfile;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.secrets.vault.AuthProfileVault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.util.Optional;

/**
 * Wires {@link QwenProviderClient} as the effective {@link ProviderClient}
 * when {@code openclaw.providers.qwen.enabled=true} AND an api-key is provided.
 *
 * <p>Declared with {@code @AutoConfiguration(before = OpenClawAutoReplyAutoConfiguration.class)}
 * so Spring evaluates THIS class first: when enabled, the Qwen bean is registered
 * before the mock's {@link ConditionalOnMissingBean} check, so the mock is skipped.
 * Without that ordering the two autoconfigs race and the mock may win.
 */
@AutoConfiguration(before = OpenClawAutoReplyAutoConfiguration.class)
@AutoConfigureAfter(name = "com.openclaw.secrets.OpenClawSecretsJdbcAutoConfiguration")
@ConditionalOnProperty(prefix = "openclaw.providers.qwen", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(QwenProviderProperties.class)
public class OpenClawProvidersQwenAutoConfiguration {

    /** Stable providerId — must match {@link QwenProviderClient#providerId()}. */
    private static final String PROVIDER_ID = "qwen";

    private static final Logger log = LoggerFactory.getLogger(OpenClawProvidersQwenAutoConfiguration.class);

    /**
     * Resolves the effective apiKey for Qwen, preferring the first vault entry
     * over the {@code openclaw.providers.qwen.api-key} property. Exposed as a
     * bean so both {@link RestClient} and {@link WebClient} factories see the
     * same value (one vault round-trip per context, not per bean).
     */
    @Bean
    @ConditionalOnMissingBean(name = "qwenApiKey")
    public String qwenApiKey(final QwenProviderProperties properties,
                              final ObjectProvider<AuthProfileVault> vaultProvider) {
        final Optional<String> fromVault = vaultProvider.stream()
            .map(v -> v.findFirst(PROVIDER_ID).map(AuthProfile::apiKey).orElse(null))
            .filter(StringUtils::hasText)
            .findFirst();
        if (fromVault.isPresent()) {
            log.info("qwen.apiKey.source=vault");
            return fromVault.get();
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException(
                "openclaw.providers.qwen.enabled=true but no apiKey is available. "
                    + "Either (a) set DASHSCOPE_API_KEY / openclaw.providers.qwen.api-key, "
                    + "or (b) upsert an AuthProfile(providerId=qwen, ...) into the vault."
            );
        }
        log.info("qwen.apiKey.source=properties");
        return properties.getApiKey();
    }

    @Bean
    @ConditionalOnMissingBean(name = "qwenRestClient")
    public RestClient qwenRestClient(final QwenProviderProperties properties,
                                      final String qwenApiKey) {
        final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.getTimeout())
            .build();
        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());

        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + qwenApiKey)
            .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "qwenWebClient")
    public WebClient qwenWebClient(final QwenProviderProperties properties,
                                    final String qwenApiKey) {
        return WebClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + qwenApiKey)
            .build();
    }

    /**
     * Registered under a provider-specific bean name so it can coexist with other
     * {@link ProviderClient}s (e.g. Google). When multiple providers are enabled,
     * {@code openclaw-providers-registry} aggregates them behind a {@code @Primary}
     * composite so {@link com.openclaw.autoreply.AutoReplyPipeline} still resolves a
     * single bean by type.
     */
    @Bean
    @ConditionalOnMissingBean(name = "qwenProviderClient")
    public ProviderClient qwenProviderClient(final QwenProviderProperties properties,
                                             final RestClient qwenRestClient,
                                             final WebClient qwenWebClient) {
        log.info("qwen.provider.enabled baseUrl={} defaultModel={} timeout={}",
            properties.getBaseUrl(), properties.getDefaultModel(), properties.getTimeout());
        return new QwenProviderClient(properties, qwenRestClient, qwenWebClient);
    }
}
