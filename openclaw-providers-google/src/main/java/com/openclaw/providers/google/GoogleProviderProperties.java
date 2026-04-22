package com.openclaw.providers.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for the Google Gemini provider.
 * <p>
 * See {@code .cursor/plan/05-translation-conventions.md}: {@code apiKey} MUST come
 * from an environment variable or the {@code openclaw-secrets} vault — never
 * committed to git.
 */
@ConfigurationProperties(prefix = "openclaw.providers.google")
public class GoogleProviderProperties {

    private boolean enabled;

    private String apiKey;

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    private String defaultModel = "gemini-1.5-flash";

    private Duration timeout = Duration.ofSeconds(30);

    /** Opaque pass-through parameters merged into {@code generationConfig}. */
    private Map<String, Object> extras = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(final String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(final Duration timeout) {
        this.timeout = timeout;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }

    public void setExtras(final Map<String, Object> extras) {
        this.extras = extras == null ? new LinkedHashMap<>() : extras;
    }
}
