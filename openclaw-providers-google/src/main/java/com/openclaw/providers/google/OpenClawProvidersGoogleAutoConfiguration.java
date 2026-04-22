package com.openclaw.providers.google;

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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.util.Optional;

/**
 * Activates {@link GeminiProviderClient} when {@code openclaw.providers.google.enabled=true}
 * and an api-key is resolvable (vault first, properties fallback).
 *
 * <p>Ordering:
 * <ul>
 *   <li>{@code @AutoConfiguration(before = OpenClawAutoReplyAutoConfiguration.class)} ensures
 *       we register ahead of the echo-mock's {@code @ConditionalOnMissingBean} check.</li>
 *   <li>{@code @AutoConfigureAfter(..OpenClawSecretsJdbcAutoConfiguration..)} (by name, no
 *       compile dep) so when {@code openclaw.secrets.store=jdbc} is set the vault bean
 *       is already available when we resolve the apiKey.</li>
 * </ul>
 */
@AutoConfiguration(before = OpenClawAutoReplyAutoConfiguration.class)
@AutoConfigureAfter(name = "com.openclaw.secrets.OpenClawSecretsJdbcAutoConfiguration")
@ConditionalOnProperty(prefix = "openclaw.providers.google", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GoogleProviderProperties.class)
public class OpenClawProvidersGoogleAutoConfiguration {

    /** Stable providerId — must match {@link GeminiProviderClient#PROVIDER_ID}. */
    private static final String PROVIDER_ID = "google";

    private static final Logger log = LoggerFactory.getLogger(OpenClawProvidersGoogleAutoConfiguration.class);

    /** Single resolution pass for {@code apiKey} shared by the provider client bean. */
    @Bean
    @ConditionalOnMissingBean(name = "googleApiKey")
    public String googleApiKey(final GoogleProviderProperties properties,
                                final ObjectProvider<AuthProfileVault> vaultProvider) {
        final Optional<String> fromVault = vaultProvider.stream()
            .map(v -> v.findFirst(PROVIDER_ID).map(AuthProfile::apiKey).orElse(null))
            .filter(StringUtils::hasText)
            .findFirst();
        if (fromVault.isPresent()) {
            log.info("google.apiKey.source=vault");
            return fromVault.get();
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException(
                "openclaw.providers.google.enabled=true but no apiKey is available. "
                    + "Either (a) set GEMINI_API_KEY / openclaw.providers.google.api-key, "
                    + "or (b) upsert an AuthProfile(providerId=google, ...) into the vault."
            );
        }
        log.info("google.apiKey.source=properties");
        return properties.getApiKey();
    }

    @Bean
    @ConditionalOnMissingBean(name = "googleRestClient")
    public RestClient googleRestClient(final GoogleProviderProperties properties) {
        final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.getTimeout())
            .build();
        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "googleWebClient")
    public WebClient googleWebClient(final GoogleProviderProperties properties) {
        return WebClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    /**
     * Registered under a provider-specific bean name so it can coexist with
     * {@code qwenProviderClient} / other {@link ProviderClient}s — the registry
     * module wraps all of them behind a {@code @Primary} composite.
     */
    @Bean
    @ConditionalOnMissingBean(name = "googleProviderClient")
    public ProviderClient googleProviderClient(final GoogleProviderProperties properties,
                                                final RestClient googleRestClient,
                                                final WebClient googleWebClient,
                                                final String googleApiKey) {
        log.info("google.provider.enabled baseUrl={} defaultModel={} timeout={}",
            properties.getBaseUrl(), properties.getDefaultModel(), properties.getTimeout());
        return new GeminiProviderClient(properties, googleRestClient, googleWebClient, googleApiKey);
    }
}
