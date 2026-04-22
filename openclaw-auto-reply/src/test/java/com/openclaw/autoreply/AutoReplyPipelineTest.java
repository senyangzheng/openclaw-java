package com.openclaw.autoreply;

import com.openclaw.autoreply.command.ChatCommand;
import com.openclaw.autoreply.command.ChatCommandDispatcher;
import com.openclaw.channels.core.InboundMessage;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.providers.api.mock.EchoMockProviderClient;
import com.openclaw.routing.RoutingKey;
import com.openclaw.sessions.InMemorySessionRepository;
import com.openclaw.sessions.Session;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AutoReplyPipelineTest {

    @Test
    void shouldPersistTurnsAndReturnEcho() {
        final InMemorySessionRepository sessions = new InMemorySessionRepository();
        final AutoReplyPipeline pipeline = new AutoReplyPipeline(sessions, new EchoMockProviderClient());

        final InboundMessage inbound = new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of("web", "anon", "c-1"),
            "hello",
            null,
            null
        );

        final OutboundMessage reply = pipeline.handle(inbound);

        assertThat(reply.text()).isEqualTo("[mock] hello");
        assertThat(reply.replyToMessageId()).isEqualTo(inbound.messageId());

        final Session persisted = sessions.find(inbound.routingKey().toSessionKey()).orElseThrow();
        assertThat(persisted.messages()).hasSize(2);
        assertThat(persisted.messages().get(0).content()).isEqualTo("hello");
        assertThat(persisted.messages().get(1).content()).isEqualTo("[mock] hello");
    }

    @Test
    void shouldAccumulateDeltasAndPersistAssistantTurnOnStreamComplete() {
        final InMemorySessionRepository sessions = new InMemorySessionRepository();
        final ProviderClient streamingProvider = new ProviderClient() {
            @Override
            public String providerId() {
                return "stub-stream";
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                throw new UnsupportedOperationException("stream-only test provider");
            }

            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                return Flux.just(
                    (ChatChunkEvent) new ChatChunkEvent.Delta("Hello "),
                    new ChatChunkEvent.Delta("world"),
                    new ChatChunkEvent.Delta("!"),
                    new ChatChunkEvent.Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY)
                );
            }
        };
        final AutoReplyPipeline pipeline = new AutoReplyPipeline(sessions, streamingProvider);

        final InboundMessage inbound = new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of("web", "anon", "c-stream"),
            "ping",
            null,
            null
        );

        StepVerifier.create(pipeline.streamHandle(inbound))
            .expectNextCount(4)
            .verifyComplete();

        final Session persisted = sessions.find(inbound.routingKey().toSessionKey()).orElseThrow();
        assertThat(persisted.messages()).hasSize(2);
        assertThat(persisted.messages().get(0).role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(persisted.messages().get(0).content()).isEqualTo("ping");
        assertThat(persisted.messages().get(1).role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(persisted.messages().get(1).content()).isEqualTo("Hello world!");
    }

    /**
     * Command short-circuit: a matching {@link ChatCommand} produces the reply
     * and the pipeline MUST NOT call the provider. Proven here by a provider
     * stub that fails the test if {@code chat()} is invoked.
     */
    @Test
    void shouldShortCircuitProviderWhenCommandMatches() {
        final InMemorySessionRepository sessions = new InMemorySessionRepository();
        final AtomicInteger providerCalls = new AtomicInteger();
        final ProviderClient trippingProvider = new ProviderClient() {
            @Override
            public String providerId() {
                return "tripwire";
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                providerCalls.incrementAndGet();
                throw new AssertionError("provider should not be called when a command matches");
            }
        };
        final ChatCommand cmd = new ChatCommand() {
            @Override
            public String name() {
                return "greet";
            }

            @Override
            public boolean matches(final InboundMessage inbound) {
                return inbound.text().startsWith("/greet");
            }

            @Override
            public String handle(final InboundMessage inbound) {
                return "hi from command";
            }
        };
        final AutoReplyPipeline pipeline = new AutoReplyPipeline(
            sessions, trippingProvider, new ChatCommandDispatcher(List.of(cmd)));

        final InboundMessage inbound = new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of("web", "anon", "c-cmd"),
            "/greet alice",
            null,
            null
        );

        final OutboundMessage reply = pipeline.handle(inbound);

        assertThat(reply.text()).isEqualTo("hi from command");
        assertThat(providerCalls.get()).isZero();

        final Session persisted = sessions.find(inbound.routingKey().toSessionKey()).orElseThrow();
        assertThat(persisted.messages()).hasSize(2);
        assertThat(persisted.messages().get(1).role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(persisted.messages().get(1).content()).isEqualTo("hi from command");
    }

    @Test
    void shouldStreamCommandReplyAsDeltaThenDone() {
        final InMemorySessionRepository sessions = new InMemorySessionRepository();
        final ProviderClient failingStreamProvider = new ProviderClient() {
            @Override
            public String providerId() {
                return "tripwire";
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                throw new AssertionError("chat() should not be called in stream path");
            }

            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                return Flux.error(new AssertionError("streamChat should not be called when command matches"));
            }
        };
        final ChatCommand cmd = new ChatCommand() {
            @Override
            public String name() {
                return "ping";
            }

            @Override
            public boolean matches(final InboundMessage inbound) {
                return "/ping".equals(inbound.text().trim());
            }

            @Override
            public String handle(final InboundMessage inbound) {
                return "pong";
            }
        };
        final AutoReplyPipeline pipeline = new AutoReplyPipeline(
            sessions, failingStreamProvider, new ChatCommandDispatcher(List.of(cmd)));

        final InboundMessage inbound = new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of("web", "anon", "c-stream-cmd"),
            "/ping",
            null,
            null
        );

        StepVerifier.create(pipeline.streamHandle(inbound))
            .expectNextMatches(event -> event instanceof ChatChunkEvent.Delta d && d.content().equals("pong"))
            .expectNextMatches(event -> event instanceof ChatChunkEvent.Done)
            .verifyComplete();

        final Session persisted = sessions.find(inbound.routingKey().toSessionKey()).orElseThrow();
        assertThat(persisted.messages()).hasSize(2);
        assertThat(persisted.messages().get(1).content()).isEqualTo("pong");
    }
}
