package com.openclaw.providers.api;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ProviderClient#streamChat(ChatRequest)} — the default
 * adapter that wraps a blocking {@link ProviderClient#chat(ChatRequest)} —
 * emits exactly one {@code Delta} followed by a terminal {@code Done}.
 */
class ProviderClientDefaultStreamTest {

    @Test
    void shouldWrapBlockingResponseAsDeltaThenDone() {
        final ProviderClient blocking = new ProviderClient() {
            @Override
            public String providerId() {
                return "fake";
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                return new ChatResponse("fake", request.model(), "hello-world",
                    ChatResponse.FinishReason.STOP,
                    new ChatResponse.Usage(3, 2, 5),
                    Duration.ofMillis(7));
            }
        };

        final ChatRequest req = ChatRequest.of("x", List.of(ChatMessage.user("hi")));

        StepVerifier.create(blocking.streamChat(req))
            .expectNext(new ChatChunkEvent.Delta("hello-world"))
            .expectNext(new ChatChunkEvent.Done(
                ChatResponse.FinishReason.STOP,
                new ChatResponse.Usage(3, 2, 5)))
            .verifyComplete();
    }

    @Test
    void shouldPropagateRuntimeExceptionAsFluxError() {
        final ProviderClient failing = new ProviderClient() {
            @Override
            public String providerId() {
                return "boom";
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                throw new IllegalStateException("nope");
            }
        };

        StepVerifier.create(failing.streamChat(ChatRequest.of("x", List.of(ChatMessage.user("hi")))))
            .expectErrorSatisfies(err -> assertThat(err)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("nope"))
            .verify();
    }
}
