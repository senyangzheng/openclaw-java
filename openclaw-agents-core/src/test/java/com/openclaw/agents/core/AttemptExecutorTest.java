package com.openclaw.agents.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.openclaw.hooks.HookNames;
import com.openclaw.hooks.HookOutcome;
import com.openclaw.hooks.HookRunner;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.providers.registry.ProviderDispatcher;
import com.openclaw.sessions.SessionKey;
import com.openclaw.stream.AgentEvent;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class AttemptExecutorTest {

    private static final SessionKey KEY = new SessionKey("web", "u", "c");

    @Test
    void providerHappyPath_emitsDeltasAndCompletes() {
        final ProviderClient provider = new FakeProvider(Flux.just(
                new ChatChunkEvent.Delta("hel"),
                new ChatChunkEvent.Delta("lo"),
                ChatChunkEvent.Done.STOP));
        try (HookRunner runner = new HookRunner()) {
            final AttemptExecutor executor = new AttemptExecutor(ProviderDispatcher.direct(provider), runner);
            final AgentRunHandle handle = AgentRunHandle.create(KEY);
            handle.advance(AgentRunState.QUEUED_SESSION);
            handle.advance(AgentRunState.QUEUED_GLOBAL);
            final SubscribeState sub = new SubscribeState();
            final AgentRunRequest req = AgentRunRequest.of(KEY, ChatMessage.user("hi"), List.of());

            final List<AgentEvent> events = executor.execute(req, handle, sub).collectList().block();
            assertThat(events).isNotNull();
            assertThat(handle.currentState()).isEqualTo(AgentRunState.COMPLETED);
            assertThat(sub.snapshot().assistantText()).isEqualTo("hello");
        }
    }

    @Test
    void beforeAgentStartShortCircuit_skipsProvider() {
        final AtomicBoolean providerCalled = new AtomicBoolean(false);
        final ProviderClient provider = new FakeProvider(Flux.just(ChatChunkEvent.Done.STOP)) {
            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                providerCalled.set(true);
                return super.streamChat(request);
            }
        };
        try (HookRunner runner = new HookRunner()) {
            runner.registerModifying(HookNames.BEFORE_AGENT_START, "cmd-hello", 100,
                    (evt, ctx) -> HookOutcome.shortCircuit("hi alice"));

            final AttemptExecutor executor = new AttemptExecutor(ProviderDispatcher.direct(provider), runner);
            final AgentRunHandle handle = AgentRunHandle.create(KEY);
            handle.advance(AgentRunState.QUEUED_SESSION);
            handle.advance(AgentRunState.QUEUED_GLOBAL);
            final SubscribeState sub = new SubscribeState();
            final AgentRunRequest req = AgentRunRequest.of(KEY, ChatMessage.user("/hello alice"), List.of());

            final List<AgentEvent> events = executor.execute(req, handle, sub).collectList().block();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(AgentEvent.Delta.class);
            assertThat(events.get(1)).isInstanceOf(AgentEvent.Done.class);
            assertThat(providerCalled).isFalse();
            assertThat(handle.currentState()).isEqualTo(AgentRunState.COMPLETED);
            assertThat(sub.snapshot().assistantText()).isEqualTo("hi alice");
        }
    }

    @Test
    void beforeAgentStartModify_mergesSystemPromptAndProviderExtras() {
        final FakeProvider provider = new FakeProvider(Flux.just(ChatChunkEvent.Done.STOP));
        try (HookRunner runner = new HookRunner()) {
            runner.registerModifying(HookNames.BEFORE_AGENT_START, "inject-sys", 100,
                    (evt, ctx) -> HookOutcome.modify(Map.of(
                            "systemPrompt", "you are helpful",
                            "providerExtras", Map.of("temperature", 0.2))));

            final AttemptExecutor executor = new AttemptExecutor(ProviderDispatcher.direct(provider), runner);
            final AgentRunHandle handle = AgentRunHandle.create(KEY);
            handle.advance(AgentRunState.QUEUED_SESSION);
            handle.advance(AgentRunState.QUEUED_GLOBAL);
            final SubscribeState sub = new SubscribeState();
            final AgentRunRequest req = AgentRunRequest.of(KEY, ChatMessage.user("hi"), List.of());

            executor.execute(req, handle, sub).blockLast();

            assertThat(provider.lastRequest).isNotNull();
            assertThat(provider.lastRequest.messages()).hasSize(2);
            assertThat(provider.lastRequest.messages().get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM);
            assertThat(provider.lastRequest.messages().get(0).content()).isEqualTo("you are helpful");
            assertThat(provider.lastRequest.extras()).containsEntry("temperature", 0.2);
        }
    }

    @Test
    void runAgentEndFiresOnHappyPath() {
        final AtomicBoolean endFired = new AtomicBoolean(false);
        try (HookRunner runner = new HookRunner()) {
            runner.registerVoid(HookNames.RUN_AGENT_END, "track-end", 100,
                    (evt, ctx) -> endFired.set(true));

            final AttemptExecutor executor = new AttemptExecutor(
                    ProviderDispatcher.direct(new FakeProvider(Flux.just(ChatChunkEvent.Done.STOP))),
                    runner);
            final AgentRunHandle handle = AgentRunHandle.create(KEY);
            handle.advance(AgentRunState.QUEUED_SESSION);
            handle.advance(AgentRunState.QUEUED_GLOBAL);
            final SubscribeState sub = new SubscribeState();
            executor.execute(
                    AgentRunRequest.of(KEY, ChatMessage.user("hi"), List.of()),
                    handle,
                    sub).blockLast();

            // run_agent_end runs asynchronously — give it a beat to complete
            await(() -> endFired.get(), 2_000L);
            assertThat(endFired).isTrue();
        }
    }

    private static void await(final java.util.function.BooleanSupplier cond, final long timeoutMs) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static class FakeProvider implements ProviderClient {
        private final Flux<ChatChunkEvent> stream;
        ChatRequest lastRequest;

        FakeProvider(final Flux<ChatChunkEvent> stream) {
            this.stream = stream;
        }

        @Override
        public String providerId() {
            return "fake";
        }

        @Override
        public ChatResponse chat(final ChatRequest request) {
            this.lastRequest = request;
            return new ChatResponse("fake", "fake-m", "ok",
                    ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY, java.time.Duration.ZERO);
        }

        @Override
        public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
            this.lastRequest = request;
            return stream;
        }
    }
}
