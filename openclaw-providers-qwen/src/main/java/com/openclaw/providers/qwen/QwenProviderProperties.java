package com.openclaw.providers.qwen;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the Qwen (Alibaba DashScope) provider, exposed at
 * {@code openclaw.providers.qwen.*} in {@code application.yml}.
 *
 * <p>The Qwen client talks to DashScope's <b>OpenAI-compatible mode</b>
 * ({@code {baseUrl}/chat/completions}) rather than the native DashScope endpoint,
 * so request/response shapes are trivially portable to any other OpenAI-like
 * vendor in the future.
 */
@ConfigurationProperties(prefix = "openclaw.providers.qwen")
public class QwenProviderProperties {

    /** Master switch; when {@code false} (default) no Qwen bean is registered. */
    private boolean enabled = false;

    /** DashScope API key — read from {@code DASHSCOPE_API_KEY} env var in examples. */
    private String apiKey;

    /** Base URL of the OpenAI-compatible endpoint. Overridable for staging / proxy. */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** Model id used when the inbound {@code ChatRequest.model()} is {@code null}. */
    private String defaultModel = "qwen-turbo";

    /** Upper-bound wall-clock timeout for a single (non-streaming) completion call. */
    private Duration timeout = Duration.ofSeconds(60);

    /**
     * Request-level sampling / provider extras merged into the JSON body (flat keys).
     * Common entries: {@code temperature}, {@code top_p}, {@code max_tokens}.
     * Per-call {@code ChatRequest.extras()} takes precedence over this map.
     */
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
