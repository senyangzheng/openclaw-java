package com.openclaw.providers.registry;

import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.CooldownPolicy;
import com.openclaw.providers.api.ProviderClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeProviderClientTest {

    private static ProviderClient ok(final String id) {
        return new ProviderClient() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                return new ChatResponse(id, "m", id + "-ok",
                    ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY, Duration.ZERO);
            }

            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                return Flux.just(
                    (ChatChunkEvent) new ChatChunkEvent.Delta(id + "-stream"),
                    new ChatChunkEvent.Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY)
                );
            }
        };
    }

    private static ProviderClient failing(final String id, final AtomicInteger invocations) {
        return new ProviderClient() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                invocations.incrementAndGet();
                throw new RuntimeException(id + "-boom");
            }

            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                invocations.incrementAndGet();
                return Flux.error(new RuntimeException(id + "-stream-boom"));
            }
        };
    }

    @Test
    void shouldReturnFirstSuccessfulProviderResponseInBlockingMode() {
        final AtomicInteger googleCalls = new AtomicInteger();
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(failing("google", googleCalls), ok("qwen")),
            List.of("google", "qwen"),
            CooldownPolicy.DEFAULT);

        final CompositeProviderClient composite = CompositeProviderClient.of(registry);
        final ChatResponse response = composite.chat(
            ChatRequest.of(null, List.of(ChatMessage.user("hi"))));

        assertThat(response.content()).isEqualTo("qwen-ok");
        assertThat(googleCalls).hasValue(1);
        assertThat(registry.isCoolingDown("google")).isTrue();
        assertThat(registry.isCoolingDown("qwen")).isFalse();
    }

    @Test
    void shouldPropagateLastErrorWhenEveryProviderFails() {
        final AtomicInteger googleCalls = new AtomicInteger();
        final AtomicInteger qwenCalls = new AtomicInteger();
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(failing("google", googleCalls), failing("qwen", qwenCalls)),
            List.of("google", "qwen"),
            CooldownPolicy.DEFAULT);

        final CompositeProviderClient composite = CompositeProviderClient.of(registry);

        assertThatThrownBy(() -> composite.chat(
                ChatRequest.of(null, List.of(ChatMessage.user("hi")))))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("qwen-boom");

        assertThat(googleCalls).hasValue(1);
        assertThat(qwenCalls).hasValue(1);
        assertThat(registry.isCoolingDown("google")).isTrue();
        assertThat(registry.isCoolingDown("qwen")).isTrue();
    }

    @Test
    void shouldFallBackOnStreamError() {
        final AtomicInteger googleCalls = new AtomicInteger();
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(failing("google", googleCalls), ok("qwen")),
            List.of("google", "qwen"),
            CooldownPolicy.DEFAULT);

        final CompositeProviderClient composite = CompositeProviderClient.of(registry);

        StepVerifier.create(composite.streamChat(ChatRequest.of(null,
                List.of(ChatMessage.user("hi")))))
            .expectNext(new ChatChunkEvent.Delta("qwen-stream"))
            .expectNext(new ChatChunkEvent.Done(
                ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY))
            .verifyComplete();

        assertThat(registry.isCoolingDown("google")).isTrue();
    }

    @Test
    void shouldReportProviderIdAsRegistry() {
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(ok("only")), List.of(), CooldownPolicy.DEFAULT);

        assertThat(CompositeProviderClient.of(registry).providerId()).isEqualTo("registry");
    }
}
