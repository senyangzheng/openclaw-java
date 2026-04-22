package com.openclaw.providers.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * SSE streaming contract tests for {@link GeminiProviderClient#streamChat}.
 * Gemini SSE frames are full candidate envelopes (not deltas on top of a prefix),
 * so each frame contributes one Delta carrying exactly its own text parts.
 */
class GeminiProviderStreamTest {

    private WireMockServer wireMock;
    private GeminiProviderClient client;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        final GoogleProviderProperties properties = new GoogleProviderProperties();
        properties.setEnabled(true);
        properties.setApiKey("api-test-key");
        properties.setBaseUrl("http://localhost:" + wireMock.port());
        properties.setDefaultModel("gemini-1.5-flash");
        properties.setTimeout(Duration.ofSeconds(5));

        final RestClient restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
            .build();
        final WebClient webClient = WebClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
        client = new GeminiProviderClient(properties, restClient, webClient);
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    @Test
    void shouldEmitDeltasAndDoneForStreamingResponse() {
        final String sseBody = """
            data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":", world"}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"!"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":2,"candidatesTokenCount":3,"totalTokenCount":5}}

            """;
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:streamGenerateContent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream;charset=UTF-8")
                .withBody(sseBody)));

        StepVerifier.create(client.streamChat(ChatRequest.of(null,
                List.of(ChatMessage.user("hi")))))
            .expectNext(new ChatChunkEvent.Delta("Hello"))
            .expectNext(new ChatChunkEvent.Delta(", world"))
            .expectNext(new ChatChunkEvent.Delta("!"))
            .expectNext(new ChatChunkEvent.Done(
                ChatResponse.FinishReason.STOP,
                new ChatResponse.Usage(2, 3, 5)))
            .verifyComplete();

        wireMock.verify(postRequestedFor(urlPathEqualTo("/models/gemini-1.5-flash:streamGenerateContent"))
            .withHeader("x-goog-api-key", equalTo("api-test-key"))
            .withHeader("Accept", equalTo("text/event-stream"))
            .withQueryParam("alt", equalTo("sse")));
    }

    @Test
    void shouldMapSafetyFinishReasonToContentFilter() {
        final String sseBody = """
            data: {"candidates":[{"content":{"parts":[{"text":"redacted"}]},"finishReason":"SAFETY"}]}

            """;
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:streamGenerateContent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream;charset=UTF-8")
                .withBody(sseBody)));

        StepVerifier.create(client.streamChat(ChatRequest.of(null,
                List.of(ChatMessage.user("unsafe")))))
            .expectNext(new ChatChunkEvent.Delta("redacted"))
            .expectNext(new ChatChunkEvent.Done(
                ChatResponse.FinishReason.CONTENT_FILTER, ChatResponse.Usage.EMPTY))
            .verifyComplete();
    }
}
