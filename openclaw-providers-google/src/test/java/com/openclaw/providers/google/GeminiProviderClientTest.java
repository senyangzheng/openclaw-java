package com.openclaw.providers.google;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline contract tests for {@link GeminiProviderClient}. Covers:
 * <ul>
 *   <li>Blocking {@code generateContent} happy path + request shaping (role mapping,
 *       systemInstruction, generationConfig merge).</li>
 *   <li>Auth header ({@code x-goog-api-key}).</li>
 *   <li>Error / malformed-JSON paths.</li>
 * </ul>
 * Streaming flow is covered in {@link GeminiProviderStreamTest}.
 */
class GeminiProviderClientTest {

    private WireMockServer wireMock;
    private GoogleProviderProperties properties;
    private GeminiProviderClient client;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        properties = new GoogleProviderProperties();
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
    void shouldReturnAssistantContentOnSuccessfulResponse() {
        final String responseBody = """
            {
              "candidates": [
                {
                  "content": {
                    "role": "model",
                    "parts": [{"text": "hello from gemini"}]
                  },
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 4,
                "candidatesTokenCount": 6,
                "totalTokenCount": 10
              }
            }
            """;
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));

        final ChatResponse response = client.chat(ChatRequest.of(null,
            List.of(ChatMessage.user("hi"))));

        assertThat(response.provider()).isEqualTo("google");
        assertThat(response.content()).isEqualTo("hello from gemini");
        assertThat(response.finishReason()).isEqualTo(ChatResponse.FinishReason.STOP);
        assertThat(response.usage().promptTokens()).isEqualTo(4);
        assertThat(response.usage().completionTokens()).isEqualTo(6);
        assertThat(response.usage().totalTokens()).isEqualTo(10);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
            .withHeader("x-goog-api-key", equalTo("api-test-key"))
            .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    void shouldMapSystemToSystemInstructionAndAssistantToModelRole() {
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]},\"finishReason\":\"STOP\"}]}")));

        client.chat(new ChatRequest(null, List.of(
            ChatMessage.system("be terse"),
            ChatMessage.user("hi"),
            ChatMessage.assistant("hello"),
            ChatMessage.user("again")
        ), Map.of("temperature", 0.3)));

        final var captured = wireMock.findAll(postRequestedFor(
            urlPathEqualTo("/models/gemini-1.5-flash:generateContent")));
        assertThat(captured).hasSize(1);
        final JSONObject body = JSON.parseObject(captured.get(0).getBodyAsString());

        assertThat(body.getJSONObject("systemInstruction").getJSONArray("parts")
            .getJSONObject(0).getString("text")).isEqualTo("be terse");

        final var contents = body.getJSONArray("contents");
        assertThat(contents).hasSize(3);
        assertThat(contents.getJSONObject(0).getString("role")).isEqualTo("user");
        assertThat(contents.getJSONObject(1).getString("role")).isEqualTo("model");
        assertThat(contents.getJSONObject(2).getString("role")).isEqualTo("user");

        assertThat(body.getJSONObject("generationConfig").getDoubleValue("temperature"))
            .isEqualTo(0.3);
    }

    @Test
    void shouldFallBackToDefaultModelWhenRequestModelIsNull() {
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]},\"finishReason\":\"STOP\"}]}")));

        final ChatResponse response = client.chat(ChatRequest.of(null,
            List.of(ChatMessage.user("hi"))));

        assertThat(response.model()).isEqualTo("gemini-1.5-flash");
    }

    @Test
    void shouldRaiseOn4xxResponse() {
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
            .willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"forbidden\"}}")));

        assertThatThrownBy(() -> client.chat(ChatRequest.of(null,
                List.of(ChatMessage.user("hi")))))
            .isInstanceOf(GeminiApiException.class)
            .satisfies(e -> assertThat(((GeminiApiException) e).status()).isEqualTo(403));
    }

    @Test
    void shouldRaiseOnMalformedJson() {
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("not json")));

        assertThatThrownBy(() -> client.chat(ChatRequest.of(null,
                List.of(ChatMessage.user("hi")))))
            .isInstanceOf(GeminiApiException.class);
    }

    @Test
    void shouldRaiseOnEmptyCandidates() {
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"candidates\":[]}")));

        assertThatThrownBy(() -> client.chat(ChatRequest.of(null,
                List.of(ChatMessage.user("hi")))))
            .isInstanceOf(GeminiApiException.class)
            .hasMessageContaining("no candidates");
    }

    @Test
    void shouldMapMaxTokensToLengthFinishReason() {
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"cut\"}]},\"finishReason\":\"MAX_TOKENS\"}]}")));

        final ChatResponse response = client.chat(ChatRequest.of(null,
            List.of(ChatMessage.user("long-prompt"))));

        assertThat(response.finishReason()).isEqualTo(ChatResponse.FinishReason.LENGTH);
    }
}
