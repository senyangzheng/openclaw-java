package com.openclaw.autoreply;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.openclaw.agents.core.ActiveRunRegistry;
import com.openclaw.agents.core.AttemptExecutor;
import com.openclaw.agents.core.PiAgentRunner;
import com.openclaw.agents.core.hooks.BeforeAgentStartEvent;
import com.openclaw.channels.core.InboundMessage;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.hooks.HookNames;
import com.openclaw.hooks.HookOutcome;
import com.openclaw.hooks.HookRunner;
import com.openclaw.lanes.SessionLaneCoordinator;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.providers.api.mock.EchoMockProviderClient;
import com.openclaw.providers.registry.ProviderDispatcher;
import com.openclaw.routing.RoutingKey;
import com.openclaw.sessions.InMemorySessionRepository;
import com.openclaw.sessions.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class AutoReplyPipelineTest {

    private final SessionLaneCoordinator lanes = new SessionLaneCoordinator();
    private final ActiveRunRegistry activeRuns = new ActiveRunRegistry();

    @AfterEach
    void tearDown() {
        lanes.close();
    }

    private AutoReplyPipeline buildPipeline(final InMemorySessionRepository sessions,
                                            final ProviderClient provider,
                                            final HookRunner hookRunner) {
        final AttemptExecutor attempt = new AttemptExecutor(ProviderDispatcher.direct(provider), hookRunner);
        final PiAgentRunner runner = new PiAgentRunner(lanes, activeRuns, attempt);
        return new AutoReplyPipeline(sessions, runner);
    }

    @Test
    void shouldPersistTurnsAndReturnEcho() {
        final InMemorySessionRepository sessions = new InMemorySessionRepository();
        final AutoReplyPipeline pipeline = buildPipeline(sessions, new EchoMockProviderClient(), new HookRunner());

        final InboundMessage inbound = new InboundMessage(
                UUID.randomUUID().toString(),
                RoutingKey.of("web", "anon", "c-1"),
                "hello",
                null,
                null);

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
                        new ChatChunkEvent.Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY));
            }
        };
        final AutoReplyPipeline pipeline = buildPipeline(sessions, streamingProvider, new HookRunner());

        final InboundMessage inbound = new InboundMessage(
                UUID.randomUUID().toString(),
                RoutingKey.of("web", "anon", "c-stream"),
                "ping",
                null,
                null);

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
     * Hook short-circuit: a {@code before_agent_start} hook returns {@link HookOutcome#shortCircuit(String)},
     * which MUST skip the provider. Proven here by a tripwire provider that fails the test on {@code chat()}.
     */
    @Test
    void shouldShortCircuitProviderWhenBeforeAgentStartHookReturnsShortCircuit() {
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
                throw new AssertionError("provider.chat should not be called when a hook short-circuits");
            }

            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                providerCalls.incrementAndGet();
                return Flux.error(new AssertionError("streamChat should not be called when hook short-circuits"));
            }
        };
        final HookRunner hookRunner = new HookRunner();
        hookRunner.registerModifying(HookNames.BEFORE_AGENT_START, "test.greet", 100,
                (BeforeAgentStartEvent event, com.openclaw.hooks.HookContext ctx) -> {
                    final String text = event.userMessage() == null ? "" : event.userMessage().content();
                    if (text != null && text.startsWith("/greet")) {
                        return HookOutcome.shortCircuit("hi from hook");
                    }
                    return HookOutcome.EMPTY;
                });
        final AutoReplyPipeline pipeline = buildPipeline(sessions, trippingProvider, hookRunner);

        final InboundMessage inbound = new InboundMessage(
                UUID.randomUUID().toString(),
                RoutingKey.of("web", "anon", "c-cmd"),
                "/greet alice",
                null,
                null);

        final OutboundMessage reply = pipeline.handle(inbound);

        assertThat(reply.text()).isEqualTo("hi from hook");
        assertThat(providerCalls.get()).isZero();

        final Session persisted = sessions.find(inbound.routingKey().toSessionKey()).orElseThrow();
        assertThat(persisted.messages()).hasSize(2);
        assertThat(persisted.messages().get(1).role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(persisted.messages().get(1).content()).isEqualTo("hi from hook");
    }

    @Test
    void shouldStreamHookShortCircuitReplyAsDeltaThenDone() {
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
                return Flux.error(new AssertionError("streamChat should not be called on short-circuit"));
            }
        };
        final HookRunner hookRunner = new HookRunner();
        hookRunner.registerModifying(HookNames.BEFORE_AGENT_START, "test.ping", 100,
                (BeforeAgentStartEvent event, com.openclaw.hooks.HookContext ctx) -> {
                    final String text = event.userMessage() == null ? "" : event.userMessage().content();
                    if ("/ping".equals(text == null ? "" : text.trim())) {
                        return HookOutcome.shortCircuit("pong");
                    }
                    return HookOutcome.EMPTY;
                });
        final AutoReplyPipeline pipeline = buildPipeline(sessions, failingStreamProvider, hookRunner);

        final InboundMessage inbound = new InboundMessage(
                UUID.randomUUID().toString(),
                RoutingKey.of("web", "anon", "c-stream-cmd"),
                "/ping",
                null,
                null);

        StepVerifier.create(pipeline.streamHandle(inbound))
                .expectNextMatches(event -> event instanceof ChatChunkEvent.Delta d && d.content().equals("pong"))
                .expectNextMatches(event -> event instanceof ChatChunkEvent.Done)
                .verifyComplete();

        final Session persisted = sessions.find(inbound.routingKey().toSessionKey()).orElseThrow();
        assertThat(persisted.messages()).hasSize(2);
        assertThat(persisted.messages().get(1).content()).isEqualTo("pong");
    }

    @Test
    void shouldSurfaceHookBlockAsErrorReply() {
        final InMemorySessionRepository sessions = new InMemorySessionRepository();
        final ProviderClient trippingProvider = new ProviderClient() {
            @Override
            public String providerId() {
                return "tripwire";
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                throw new AssertionError("provider.chat should not be called when a hook blocks");
            }

            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                return Flux.error(new AssertionError("streamChat should not be called when hook blocks"));
            }
        };
        final HookRunner hookRunner = new HookRunner();
        hookRunner.registerModifying(HookNames.BEFORE_AGENT_START, "test.block", 100,
                (BeforeAgentStartEvent event, com.openclaw.hooks.HookContext ctx) -> HookOutcome.block("policy.denied"));
        final AutoReplyPipeline pipeline = buildPipeline(sessions, trippingProvider, hookRunner);

        final InboundMessage inbound = new InboundMessage(
                UUID.randomUUID().toString(),
                RoutingKey.of("web", "anon", "c-block"),
                "any text",
                null,
                null);

        final OutboundMessage reply = pipeline.handle(inbound);
        assertThat(reply.text()).contains("policy.denied");
    }
}
