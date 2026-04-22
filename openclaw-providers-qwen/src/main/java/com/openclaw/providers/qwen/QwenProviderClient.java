package com.openclaw.providers.qwen;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.providers.api.ToolCallChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;
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
 * {@link ProviderClient} backed by Qwen (Alibaba DashScope) in its
 * <b>OpenAI-compatible mode</b> — {@code POST {baseUrl}/chat/completions}.
 *
 * <p>Only blocking completions are supported at M1; streaming will be added in M2
 * together with {@code Flux<ChatChunkEvent>} on the SPI.
 *
 * <p>Request body ({@code stream: false}):
 * <pre>{@code
 * {
 *   "model": "qwen-turbo",
 *   "messages": [{"role": "user", "content": "hi"}],
 *   "temperature": 0.7
 * }
 * }</pre>
 */
public class QwenProviderClient implements ProviderClient {

    public static final String PROVIDER_ID = "qwen";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String SSE_DONE_SENTINEL = "[DONE]";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final Logger log = LoggerFactory.getLogger(QwenProviderClient.class);

    private final QwenProviderProperties properties;
    private final RestClient restClient;
    private final WebClient webClient;

    public QwenProviderClient(final QwenProviderProperties properties,
                              final RestClient restClient,
                              final WebClient webClient) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.webClient = Objects.requireNonNull(webClient, "webClient");
    }

    /**
     * Legacy constructor kept for callers / tests that don't need streaming.
     * {@link #streamChat(ChatRequest)} will fall back to the default blocking
     * adapter on this code path.
     */
    public QwenProviderClient(final QwenProviderProperties properties, final RestClient restClient) {
        this(properties, restClient, WebClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
            .build());
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ChatResponse chat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");

        final String model = request.model() != null ? request.model() : properties.getDefaultModel();
        final String body = buildRequestBody(model, request);
        final Instant started = Instant.now();

        final String rawResponse;
        try {
            // Authorization header is pre-installed on the RestClient default headers
            // (see OpenClawProvidersQwenAutoConfiguration#qwenRestClient) so we don't
            // need to attach it per request — attaching it again overrides the default
            // when the apiKey only lives in the vault (properties.apiKey = null).
            rawResponse = restClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("qwen.call.failed status={} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new QwenApiException(e.getStatusCode().value(),
                "Qwen responded " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            log.warn("qwen.call.error", e);
            throw new QwenApiException("Qwen call failed: " + e.getMessage(), e);
        }

        return parseResponse(model, rawResponse, Duration.between(started, Instant.now()));
    }

    @Override
    public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final String model = request.model() != null ? request.model() : properties.getDefaultModel();
        final String body = buildRequestBody(model, request, true);
        final AtomicReference<ChatResponse.FinishReason> finishRef = new AtomicReference<>();
        final AtomicReference<ChatResponse.Usage> usageRef = new AtomicReference<>();

        // Same deal as chat(): Authorization lives on WebClient default headers.
        return webClient.post()
            .uri(CHAT_COMPLETIONS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(SSE_TYPE)
            .takeUntil(sse -> SSE_DONE_SENTINEL.equals(sse.data()))
            .concatMap(sse -> decodeSseChunk(sse, finishRef, usageRef))
            .concatWith(Flux.defer(() -> Flux.just((ChatChunkEvent) new ChatChunkEvent.Done(
                finishRef.get() != null ? finishRef.get() : ChatResponse.FinishReason.STOP,
                usageRef.get() != null ? usageRef.get() : ChatResponse.Usage.EMPTY))))
            .onErrorResume(err -> {
                log.warn("qwen.stream.error", err);
                return Flux.error(new QwenApiException("Qwen stream failed: " + err.getMessage(), err));
            });
    }

    /**
     * Decodes a single SSE frame into zero-or-more {@link ChatChunkEvent}s and updates
     * the finishReason / usage refs in-place for the trailing {@link ChatChunkEvent.Done}.
     */
    private static Flux<ChatChunkEvent> decodeSseChunk(final ServerSentEvent<String> sse,
                                                       final AtomicReference<ChatResponse.FinishReason> finishRef,
                                                       final AtomicReference<ChatResponse.Usage> usageRef) {
        final String data = sse.data();
        if (data == null || data.isBlank() || SSE_DONE_SENTINEL.equals(data)) {
            return Flux.empty();
        }
        final JSONObject json;
        try {
            json = JSON.parseObject(data);
        } catch (RuntimeException e) {
            log.debug("qwen.stream.parse.skip data={}", data);
            return Flux.empty();
        }
        if (json == null) {
            return Flux.empty();
        }

        final var usage = json.getJSONObject("usage");
        if (usage != null) {
            usageRef.set(parseUsage(usage));
        }

        final var choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return Flux.empty();
        }
        final JSONObject firstChoice = choices.getJSONObject(0);
        final String finish = firstChoice.getString("finish_reason");
        if (finish != null) {
            finishRef.set(mapFinishReason(finish));
        }

        final JSONObject delta = firstChoice.getJSONObject("delta");
        if (delta == null) {
            return Flux.empty();
        }

        final List<ChatChunkEvent> events = new ArrayList<>(2);

        final var toolCalls = delta.getJSONArray("tool_calls");
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.size(); i++) {
                final JSONObject tc = toolCalls.getJSONObject(i);
                if (tc == null) {
                    continue;
                }
                final JSONObject fn = tc.getJSONObject("function");
                events.add(new ChatChunkEvent.ToolCall(new ToolCallChunk(
                    tc.getString("id") != null ? tc.getString("id") : "tc-" + i,
                    tc.getIntValue("index", i),
                    fn != null ? fn.getString("name") : null,
                    fn != null ? fn.getString("arguments") : null
                )));
            }
        }

        final String content = delta.getString("content");
        if (content != null && !content.isEmpty()) {
            events.add(new ChatChunkEvent.Delta(content));
        }

        return events.isEmpty() ? Flux.empty() : Flux.fromIterable(events);
    }

    private String buildRequestBody(final String model, final ChatRequest request) {
        return buildRequestBody(model, request, false);
    }

    private String buildRequestBody(final String model, final ChatRequest request, final boolean stream) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", stream);

        final List<Map<String, Object>> messages = new ArrayList<>(request.messages().size());
        for (final ChatMessage message : request.messages()) {
            messages.add(Map.of(
                "role", message.role().name().toLowerCase(Locale.ROOT),
                "content", message.content()
            ));
        }
        body.put("messages", messages);

        properties.getExtras().forEach(body::put);
        request.extras().forEach(body::put);

        return JSON.toJSONString(body);
    }

    private ChatResponse parseResponse(final String requestedModel,
                                       final String rawResponse,
                                       final Duration elapsed) {
        final JSONObject json;
        try {
            json = JSON.parseObject(rawResponse);
        } catch (RuntimeException e) {
            throw new QwenApiException("Qwen response was not valid JSON: " + rawResponse, e);
        }
        if (json == null) {
            throw new QwenApiException(200, "Qwen returned empty response body");
        }

        final var choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new QwenApiException(200, "Qwen response has no choices: " + rawResponse);
        }
        final JSONObject firstChoice = choices.getJSONObject(0);
        final JSONObject message = firstChoice.getJSONObject("message");
        if (message == null) {
            throw new QwenApiException(200, "Qwen choice has no message: " + rawResponse);
        }
        final String content = message.getString("content");
        if (content == null) {
            throw new QwenApiException(200, "Qwen message has no content: " + rawResponse);
        }

        final ChatResponse.FinishReason finishReason = mapFinishReason(firstChoice.getString("finish_reason"));
        final String model = json.getString("model");
        final ChatResponse.Usage usage = parseUsage(json.getJSONObject("usage"));

        return new ChatResponse(
            PROVIDER_ID,
            model == null ? requestedModel : model,
            content,
            finishReason,
            usage,
            elapsed
        );
    }

    private static ChatResponse.FinishReason mapFinishReason(final String raw) {
        if (raw == null) {
            return ChatResponse.FinishReason.STOP;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "stop", "null" -> ChatResponse.FinishReason.STOP;
            case "length" -> ChatResponse.FinishReason.LENGTH;
            case "tool_calls" -> ChatResponse.FinishReason.TOOL_CALLS;
            case "content_filter" -> ChatResponse.FinishReason.CONTENT_FILTER;
            default -> ChatResponse.FinishReason.STOP;
        };
    }

    private static ChatResponse.Usage parseUsage(final JSONObject usage) {
        if (usage == null) {
            return ChatResponse.Usage.EMPTY;
        }
        return new ChatResponse.Usage(
            usage.getIntValue("prompt_tokens", 0),
            usage.getIntValue("completion_tokens", 0),
            usage.getIntValue("total_tokens", 0)
        );
    }
}
