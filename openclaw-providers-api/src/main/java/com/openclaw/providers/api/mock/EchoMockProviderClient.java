package com.openclaw.providers.api.mock;

import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Deterministic, dependency-free provider for M1 end-to-end smoke tests.
 * <p>
 * Echoes the last {@link ChatMessage.Role#USER} message prefixed with {@code "[mock] "}.
 * Real providers (Gemini, Qwen) arrive in M2.
 */
public class EchoMockProviderClient implements ProviderClient {

    public static final String PROVIDER_ID = "mock";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ChatResponse chat(final ChatRequest request) {
        final Instant started = Instant.now();
        final String lastUserText = request.messages().reversed().stream()
            .filter(m -> m.role() == ChatMessage.Role.USER)
            .map(ChatMessage::content)
            .findFirst()
            .orElse("");
        final String reply = "[mock] " + lastUserText;

        return new ChatResponse(
            PROVIDER_ID,
            request.model() == null ? "echo" : request.model(),
            reply,
            ChatResponse.FinishReason.STOP,
            new ChatResponse.Usage(lastUserText.length(), reply.length(), lastUserText.length() + reply.length()),
            Duration.between(started, Instant.now())
        );
    }
}
