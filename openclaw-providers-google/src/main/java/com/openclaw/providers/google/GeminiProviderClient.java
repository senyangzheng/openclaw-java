package com.openclaw.providers.google;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ProviderClient} backed by Google Gemini's REST API.
 * <p>
 * Endpoints (base URL defaults to {@code https://generativelanguage.googleapis.com/v1beta}):
 * <ul>
 *   <li>Blocking: {@code POST /models/{model}:generateContent}</li>
 *   <li>Streaming: {@code POST /models/{model}:streamGenerateContent?alt=sse}</li>
 * </ul>
 *
 * <p>Role mapping:
 * <ul>
 *   <li>{@code USER} → {@code "user"} in {@code contents}</li>
 *   <li>{@code ASSISTANT} → {@code "model"} in {@code contents}</li>
 *   <li>{@code SYSTEM} → folded into {@code systemInstruction.parts[]} (Gemini doesn't
 *       accept a {@code system} role inside {@code contents})</li>
 * </ul>
 *
 * <p>Authentication: API key is sent via the {@code x-goog-api-key} header (preferred
 * over the {@code ?key=} query parameter because it doesn't leak into access logs).
 */
public class GeminiProviderClient implements ProviderClient {

    public static final String PROVIDER_ID = "google";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final Logger log = LoggerFactory.getLogger(GeminiProviderClient.class);

    private final GoogleProviderProperties properties;
    private final RestClient restClient;
    private final WebClient webClient;
    /**
     * Effective API key — independent of {@link GoogleProviderProperties#getApiKey()}
     * so the autoconfig can source it from an {@code AuthProfileVault} and still
     * keep the rest of the properties (model, timeout, extras) intact.
     */
    private final String apiKey;

    public GeminiProviderClient(final GoogleProviderProperties properties,
                                final RestClient restClient,
                                final WebClient webClient,
                                final String apiKey) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.webClient = Objects.requireNonNull(webClient, "webClient");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    }

    /** Back-compat: the existing test suites construct the client with just the three
     *  collaborators and expect the apiKey to come from {@code properties}. */
    public GeminiProviderClient(final GoogleProviderProperties properties,
                                final RestClient restClient,
                                final WebClient webClient) {
        this(properties, restClient, webClient, properties.getApiKey());
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ChatResponse chat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final String model = request.model() != null ? request.model() : properties.getDefaultModel();
        final String body = buildRequestBody(request);
        final Instant started = Instant.now();

        final String rawResponse;
        try {
            rawResponse = restClient.post()
                .uri("/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, apiKey)
                .body(body)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("google.call.failed status={} body={}",
                e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new GeminiApiException(e.getStatusCode().value(),
                "Gemini responded " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            log.warn("google.call.error", e);
            throw new GeminiApiException("Gemini call failed: " + e.getMessage(), e);
        }

        return parseBlockingResponse(model, rawResponse, Duration.between(started, Instant.now()));
    }

    @Override
    public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final String model = request.model() != null ? request.model() : properties.getDefaultModel();
        final String body = buildRequestBody(request);
        final AtomicReference<ChatResponse.FinishReason> finishRef = new AtomicReference<>();
        final AtomicReference<ChatResponse.Usage> usageRef = new AtomicReference<>();

        return webClient.post()
            .uri(uri -> uri.path("/models/{model}:streamGenerateContent")
                .queryParam("alt", "sse")
                .build(model))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header(API_KEY_HEADER, apiKey)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(SSE_TYPE)
            .concatMap(sse -> decodeSseChunk(sse, finishRef, usageRef))
            .concatWith(Flux.defer(() -> Flux.just((ChatChunkEvent) new ChatChunkEvent.Done(
                finishRef.get() != null ? finishRef.get() : ChatResponse.FinishReason.STOP,
                usageRef.get() != null ? usageRef.get() : ChatResponse.Usage.EMPTY))))
            .onErrorResume(err -> {
                log.warn("google.stream.error", err);
                return Flux.error(new GeminiApiException("Gemini stream failed: " + err.getMessage(), err));
            });
    }

    // ---------------------------------------------------------------
    //  Request body shaping
    // ---------------------------------------------------------------

    private String buildRequestBody(final ChatRequest request) {
        final Map<String, Object> body = new LinkedHashMap<>();

        final List<Map<String, Object>> contents = new ArrayList<>();
        final List<Map<String, Object>> systemParts = new ArrayList<>();

        for (final ChatMessage message : request.messages()) {
            if (message.role() == ChatMessage.Role.SYSTEM) {
                systemParts.add(Map.of("text", message.content()));
            } else {
                contents.add(Map.of(
                    "role", message.role() == ChatMessage.Role.ASSISTANT ? "model" : "user",
                    "parts", List.of(Map.of("text", message.content()))
                ));
            }
        }
        body.put("contents", contents);
        if (!systemParts.isEmpty()) {
            body.put("systemInstruction", Map.of("parts", systemParts));
        }

        // generationConfig: merge property-level + request-level extras
        final Map<String, Object> generationConfig = new LinkedHashMap<>();
        properties.getExtras().forEach(generationConfig::put);
        request.extras().forEach(generationConfig::put);
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
        }

        return JSON.toJSONString(body);
    }

    // ---------------------------------------------------------------
    //  Response parsing (blocking)
    // ---------------------------------------------------------------

    private ChatResponse parseBlockingResponse(final String requestedModel,
                                               final String rawResponse,
                                               final Duration elapsed) {
        final JSONObject json;
        try {
            json = JSON.parseObject(rawResponse);
        } catch (RuntimeException e) {
            throw new GeminiApiException("Gemini response was not valid JSON: " + rawResponse, e);
        }
        if (json == null) {
            throw new GeminiApiException(200, "Gemini returned empty response body");
        }

        final JSONArray candidates = json.getJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new GeminiApiException(200, "Gemini response has no candidates: " + rawResponse);
        }
        final JSONObject first = candidates.getJSONObject(0);
        final String content = extractText(first.getJSONObject("content"));
        if (content == null) {
            throw new GeminiApiException(200, "Gemini candidate has no text parts: " + rawResponse);
        }
        final ChatResponse.FinishReason finish = mapFinishReason(first.getString("finishReason"));
        final ChatResponse.Usage usage = parseUsage(json.getJSONObject("usageMetadata"));

        return new ChatResponse(
            PROVIDER_ID,
            requestedModel,
            content,
            finish,
            usage,
            elapsed
        );
    }

    // ---------------------------------------------------------------
    //  Response parsing (streaming)
    // ---------------------------------------------------------------

    private static Flux<ChatChunkEvent> decodeSseChunk(
        final ServerSentEvent<String> sse,
        final AtomicReference<ChatResponse.FinishReason> finishRef,
        final AtomicReference<ChatResponse.Usage> usageRef
    ) {
        final String data = sse.data();
        if (data == null || data.isBlank()) {
            return Flux.empty();
        }
        final JSONObject json;
        try {
            json = JSON.parseObject(data);
        } catch (RuntimeException e) {
            log.debug("google.stream.parse.skip data={}", data);
            return Flux.empty();
        }
        if (json == null) {
            return Flux.empty();
        }

        final JSONObject usage = json.getJSONObject("usageMetadata");
        if (usage != null) {
            usageRef.set(parseUsage(usage));
        }

        final JSONArray candidates = json.getJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return Flux.empty();
        }
        final JSONObject first = candidates.getJSONObject(0);
        final String finish = first.getString("finishReason");
        if (finish != null) {
            finishRef.set(mapFinishReason(finish));
        }

        final String text = extractText(first.getJSONObject("content"));
        if (text == null || text.isEmpty()) {
            return Flux.empty();
        }
        return Flux.just(new ChatChunkEvent.Delta(text));
    }

    private static String extractText(final JSONObject content) {
        if (content == null) {
            return null;
        }
        final JSONArray parts = content.getJSONArray("parts");
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            final JSONObject part = parts.getJSONObject(i);
            if (part == null) {
                continue;
            }
            final String text = part.getString("text");
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static ChatResponse.FinishReason mapFinishReason(final String raw) {
        if (raw == null) {
            return ChatResponse.FinishReason.STOP;
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "STOP" -> ChatResponse.FinishReason.STOP;
            case "MAX_TOKENS" -> ChatResponse.FinishReason.LENGTH;
            case "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" ->
                ChatResponse.FinishReason.CONTENT_FILTER;
            case "TOOL_CALL" -> ChatResponse.FinishReason.TOOL_CALLS;
            default -> ChatResponse.FinishReason.STOP;
        };
    }

    private static ChatResponse.Usage parseUsage(final JSONObject usage) {
        if (usage == null) {
            return ChatResponse.Usage.EMPTY;
        }
        return new ChatResponse.Usage(
            usage.getIntValue("promptTokenCount", 0),
            usage.getIntValue("candidatesTokenCount", 0),
            usage.getIntValue("totalTokenCount", 0)
        );
    }
}
