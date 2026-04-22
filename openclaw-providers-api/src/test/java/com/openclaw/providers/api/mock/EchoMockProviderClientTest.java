package com.openclaw.providers.api.mock;

import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EchoMockProviderClientTest {

    @Test
    void shouldEchoLastUserMessage() {
        final EchoMockProviderClient client = new EchoMockProviderClient();

        final ChatResponse resp = client.chat(ChatRequest.of("echo", List.of(
            ChatMessage.system("you are mock"),
            ChatMessage.user("first question"),
            ChatMessage.assistant("first answer"),
            ChatMessage.user("second question")
        )));

        assertThat(resp.provider()).isEqualTo("mock");
        assertThat(resp.content()).isEqualTo("[mock] second question");
        assertThat(resp.finishReason()).isEqualTo(ChatResponse.FinishReason.STOP);
    }

    @Test
    void shouldReturnEmptyPrefixWhenNoUserMessage() {
        final ChatResponse resp = new EchoMockProviderClient().chat(
            ChatRequest.of("echo", List.of(ChatMessage.system("x")))
        );

        assertThat(resp.content()).isEqualTo("[mock] ");
    }
}
