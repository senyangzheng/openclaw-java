package com.openclaw.providers.qwen;

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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline contract tests for {@link QwenProviderClient} using WireMock.
 * Verifies request shape (auth, body, model fallback) and response parsing
 * without requiring network access or a real DashScope API key.
 */
class QwenProviderClientTest {

    private WireMockServer wireMock;
    private QwenProviderProperties properties;
    private QwenProviderClient client;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        properties = new QwenProviderProperties();
        properties.setEnabled(true);
        properties.setApiKey("sk-test-key");
        properties.setBaseUrl("http://localhost:" + wireMock.port());
        properties.setDefaultModel("qwen-turbo");
        properties.setTimeout(Duration.ofSeconds(5));

        // Authorization is pre-installed on the RestClient — mirrors what
        // OpenClawProvidersQwenAutoConfiguration#qwenRestClient does at runtime.
        final RestClient restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(org.springframework.http.HttpHeaders.AUTHORIZATION,
                "Bearer " + properties.getApiKey())
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
            .build();
        client = new QwenProviderClient(properties, restClient);
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    @Test
    void shouldReturnAssistantContentOnSuccessfulResponse() {
        final String responseBody = """
            {
              "id": "chatcmpl-123",
              "object": "chat.completion",
              "model": "qwen-turbo",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "hello from qwen"},
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 5, "completion_tokens": 7, "total_tokens": 12}
            }
            """;
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));

        final ChatResponse response = client.chat(ChatRequest.of(
            "qwen-turbo",
            List.of(ChatMessage.user("hi"))
        ));

        assertThat(response.provider()).isEqualTo("qwen");
        assertThat(response.model()).isEqualTo("qwen-turbo");
        assertThat(response.content()).isEqualTo("hello from qwen");
        assertThat(response.finishReason()).isEqualTo(ChatResponse.FinishReason.STOP);
        assertThat(response.usage().promptTokens()).isEqualTo(5);
        assertThat(response.usage().completionTokens()).isEqualTo(7);
        assertThat(response.usage().totalTokens()).isEqualTo(12);
        assertThat(response.elapsed()).isGreaterThanOrEqualTo(Duration.ZERO);
    }

    @Test
    void shouldSendBearerAuthAndOpenAiCompatibleBody() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]
                    }
                    """)));

        client.chat(new ChatRequest(
            "qwen-max",
            List.of(
                ChatMessage.system("be concise"),
                ChatMessage.user("ping")
            ),
            Map.of("temperature", 0.1)
        ));

        wireMock.verify(postRequestedFor(urlEqualTo("/chat/completions"))
            .withHeader("Authorization", equalTo("Bearer sk-test-key"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("qwen-max")))
            .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
            .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
            .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("be concise")))
            .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("user")))
            .withRequestBody(matchingJsonPath("$.messages[1].content", equalTo("ping"))));

        final JSONObject body = JSON.parseObject(
            wireMock.findAll(postRequestedFor(urlEqualTo("/chat/completions"))).getFirst().getBodyAsString()
        );
        assertThat(body.getDoubleValue("temperature")).isEqualTo(0.1);
    }

    @Test
    void shouldFallBackToDefaultModelWhenRequestModelIsNull() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                    """)));

        client.chat(ChatRequest.of(null, List.of(ChatMessage.user("hi"))));

        wireMock.verify(postRequestedFor(urlEqualTo("/chat/completions"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("qwen-turbo"))));
    }

    @Test
    void shouldMergePropertiesExtrasIntoRequestBody() {
        properties.getExtras().put("top_p", 0.9);
        properties.getExtras().put("max_tokens", 256);

        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                    """)));

        client.chat(ChatRequest.of("qwen-turbo", List.of(ChatMessage.user("hi"))));

        wireMock.verify(postRequestedFor(urlEqualTo("/chat/completions"))
            .withRequestBody(matchingJsonPath("$.top_p", equalTo("0.9")))
            .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("256"))));
    }

    @Test
    void shouldThrowQwenApiExceptionOnHttp4xx() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"Invalid API key\"}}")));

        assertThatThrownBy(() -> client.chat(ChatRequest.of("qwen-turbo", List.of(ChatMessage.user("hi")))))
            .isInstanceOf(QwenApiException.class)
            .satisfies(ex -> assertThat(((QwenApiException) ex).httpStatus()).isEqualTo(401))
            .hasMessageContaining("401");
    }

    @Test
    void shouldThrowQwenApiExceptionOnMalformedJson() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("not-json-at-all")));

        assertThatThrownBy(() -> client.chat(ChatRequest.of("qwen-turbo", List.of(ChatMessage.user("hi")))))
            .isInstanceOf(QwenApiException.class)
            .hasMessageContaining("not valid JSON");
    }

    @Test
    void shouldThrowQwenApiExceptionWhenChoicesAreEmpty() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"choices\":[]}")));

        assertThatThrownBy(() -> client.chat(ChatRequest.of("qwen-turbo", List.of(ChatMessage.user("hi")))))
            .isInstanceOf(QwenApiException.class)
            .hasMessageContaining("no choices");
    }

    @Test
    void shouldMapFinishReasonLength() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"choices":[{"message":{"role":"assistant","content":"truncated"},"finish_reason":"length"}]}
                    """)));

        final ChatResponse response = client.chat(
            ChatRequest.of("qwen-turbo", List.of(ChatMessage.user("hi")))
        );

        assertThat(response.finishReason()).isEqualTo(ChatResponse.FinishReason.LENGTH);
    }
}
