package com.openclaw.providers.qwen;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Offline contract tests for the streaming SSE code path. Uses WireMock's
 * static body to serve an OpenAI-compatible {@code text/event-stream} payload
 * and asserts that the resulting {@code Flux<ChatChunkEvent>} carries the
 * expected Delta / Done (and optionally ToolCall) events in order.
 */
class QwenProviderStreamTest {

    private WireMockServer wireMock;
    private QwenProviderClient client;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        final QwenProviderProperties properties = new QwenProviderProperties();
        properties.setEnabled(true);
        properties.setApiKey("sk-test-key");
        properties.setBaseUrl("http://localhost:" + wireMock.port());
        properties.setDefaultModel("qwen-turbo");
        properties.setTimeout(Duration.ofSeconds(5));

        final RestClient restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
            .build();
        final WebClient webClient = WebClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
            .build();
        client = new QwenProviderClient(properties, restClient, webClient);
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    @Test
    void shouldEmitDeltasThenDoneWhenServerSendsIncrementalSseFrames() {
        final String sseBody = """
            data: {"choices":[{"index":0,"delta":{"role":"assistant","content":""}}]}

            data: {"choices":[{"index":0,"delta":{"content":"Hello"}}]}

            data: {"choices":[{"index":0,"delta":{"content":", world"}}]}

            data: {"choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}

            data: [DONE]

            """;
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream;charset=UTF-8")
                .withBody(sseBody)));

        final ChatRequest req = ChatRequest.of("qwen-turbo",
            List.of(ChatMessage.user("hi")));

        StepVerifier.create(client.streamChat(req))
            .expectNext(new ChatChunkEvent.Delta("Hello"))
            .expectNext(new ChatChunkEvent.Delta(", world"))
            .expectNext(new ChatChunkEvent.Delta("!"))
            .expectNext(new ChatChunkEvent.Done(
                ChatResponse.FinishReason.STOP,
                new ChatResponse.Usage(3, 4, 7)))
            .verifyComplete();

        wireMock.verify(postRequestedFor(urlEqualTo("/chat/completions"))
            .withHeader("Authorization", equalTo("Bearer sk-test-key"))
            .withHeader("Accept", equalTo("text/event-stream"))
            .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.model", equalTo("qwen-turbo"))));
    }

    @Test
    void shouldMapFinishReasonLengthOnStreamDone() {
        final String sseBody = """
            data: {"choices":[{"index":0,"delta":{"content":"truncated"},"finish_reason":"length"}]}

            data: [DONE]

            """;
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream;charset=UTF-8")
                .withBody(sseBody)));

        StepVerifier.create(client.streamChat(ChatRequest.of("qwen-turbo",
                List.of(ChatMessage.user("x")))))
            .expectNext(new ChatChunkEvent.Delta("truncated"))
            .expectNext(new ChatChunkEvent.Done(
                ChatResponse.FinishReason.LENGTH, ChatResponse.Usage.EMPTY))
            .verifyComplete();
    }

    @Test
    void shouldEmitToolCallChunkWhenServerStreamsToolCalls() {
        final String sseBody = """
            data: {"choices":[{"index":0,"delta":{"tool_calls":[{"id":"call-1","index":0,"function":{"name":"sum","arguments":"{\\"a\\":"}}]}}]}

            data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"1,\\"b\\":2}"}}]}}]}

            data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

            data: [DONE]

            """;
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream;charset=UTF-8")
                .withBody(sseBody)));

        StepVerifier.create(client.streamChat(ChatRequest.of("qwen-turbo",
                List.of(ChatMessage.user("compute")))))
            .assertNext(e -> {
                ChatChunkEvent.ToolCall tc = (ChatChunkEvent.ToolCall) e;
                org.assertj.core.api.Assertions.assertThat(tc.chunk().id()).isEqualTo("call-1");
                org.assertj.core.api.Assertions.assertThat(tc.chunk().name()).isEqualTo("sum");
                org.assertj.core.api.Assertions.assertThat(tc.chunk().argumentsDelta()).isEqualTo("{\"a\":");
            })
            .assertNext(e -> {
                ChatChunkEvent.ToolCall tc = (ChatChunkEvent.ToolCall) e;
                org.assertj.core.api.Assertions.assertThat(tc.chunk().argumentsDelta()).isEqualTo("1,\"b\":2}");
            })
            .expectNext(new ChatChunkEvent.Done(
                ChatResponse.FinishReason.TOOL_CALLS, ChatResponse.Usage.EMPTY))
            .verifyComplete();
    }
}
