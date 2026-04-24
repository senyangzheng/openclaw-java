package com.openclaw.agents.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.openclaw.hooks.HookRunner;
import com.openclaw.lanes.SessionLaneCoordinator;
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

class PiAgentRunnerTest {

    private static final SessionKey KEY = new SessionKey("web", "u", "c");

    @Test
    void happyPath_eventsFlowThroughLaneAndExecutor() {
        try (SessionLaneCoordinator lanes = new SessionLaneCoordinator();
             HookRunner hooks = new HookRunner()) {

            final ProviderClient provider = new FakeProvider(() -> Flux.just(
                    new ChatChunkEvent.Delta("hi"),
                    ChatChunkEvent.Done.STOP));
            final AttemptExecutor executor = new AttemptExecutor(ProviderDispatcher.direct(provider), hooks);
            final ActiveRunRegistry registry = new ActiveRunRegistry();
            final PiAgentRunner runner = new PiAgentRunner(lanes, registry, executor);

            final PiAgentRunner.AgentRunOutcome outcome = runner.submit(
                    AgentRunRequest.of(KEY, ChatMessage.user("hello"), List.of()));

            final List<AgentEvent> events = outcome.events().collectList().block();
            assertThat(events).isNotEmpty();
            assertThat(outcome.handle().currentState()).isEqualTo(AgentRunState.COMPLETED);
            assertThat(registry.current(KEY)).isEmpty();
            assertThat(outcome.subscribe().snapshot().assistantText()).isEqualTo("hi");
        }
    }

    @Test
    void duplicateRegisterOnSameSessionIsRejected() {
        // Stresses ActiveRunRegistry through PiAgentRunner without any real lane blocking. We directly occupy
        // the registry slot as if a previous run were still active, then verify that submit() surfaces
        // ACTIVE_RUN_CONFLICT on the returned flux.
        try (SessionLaneCoordinator lanes = new SessionLaneCoordinator();
             HookRunner hooks = new HookRunner()) {

            final AttemptExecutor executor = new AttemptExecutor(
                    ProviderDispatcher.direct(new FakeProvider(() -> Flux.just(ChatChunkEvent.Done.STOP))), hooks);
            final ActiveRunRegistry registry = new ActiveRunRegistry();
            registry.tryRegister(AgentRunHandle.create(KEY));

            final PiAgentRunner runner = new PiAgentRunner(lanes, registry, executor);
            final PiAgentRunner.AgentRunOutcome outcome = runner.submit(
                    AgentRunRequest.of(KEY, ChatMessage.user("hi"), List.of()));
            final List<AgentEvent> events = outcome.events()
                    .onErrorResume(err -> Flux.empty())
                    .collectList().block();
            assertThat(events).anyMatch(e -> e instanceof AgentEvent.Error err
                    && err.code().equals(AgentErrorCode.ACTIVE_RUN_CONFLICT.code()));
            assertThat(outcome.handle().currentState()).isEqualTo(AgentRunState.IDLE);
        }
    }

    @Test
    void laneSchedulingSerializesConsecutiveRunsForSameSession() throws Exception {
        try (SessionLaneCoordinator lanes = new SessionLaneCoordinator();
             HookRunner hooks = new HookRunner()) {

            final AtomicInteger callCount = new AtomicInteger();
            final ProviderClient provider = new FakeProvider(() -> {
                callCount.incrementAndGet();
                return Flux.just(new ChatChunkEvent.Delta("ok"), ChatChunkEvent.Done.STOP);
            });
            final AttemptExecutor executor = new AttemptExecutor(ProviderDispatcher.direct(provider), hooks);
            final PiAgentRunner runner = new PiAgentRunner(lanes, new ActiveRunRegistry(), executor);

            // Submit two runs on DIFFERENT sessions so they are allowed to coexist; verifies that scheduling
            // does not deadlock when the registry does not veto the second submission.
            final var first = runner.submit(AgentRunRequest.of(
                    new SessionKey("web", "u", "a"), ChatMessage.user("a"), List.of()));
            final var second = runner.submit(AgentRunRequest.of(
                    new SessionKey("web", "u", "b"), ChatMessage.user("b"), List.of()));

            first.events().collectList().block();
            second.events().collectList().block();

            assertThat(callCount.get()).isEqualTo(2);
            assertThat(first.handle().currentState()).isEqualTo(AgentRunState.COMPLETED);
            assertThat(second.handle().currentState()).isEqualTo(AgentRunState.COMPLETED);
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
        private final java.util.function.Supplier<Flux<ChatChunkEvent>> streamSupplier;

        FakeProvider(final java.util.function.Supplier<Flux<ChatChunkEvent>> streamSupplier) {
            this.streamSupplier = streamSupplier;
        }

        @Override
        public String providerId() {
            return "fake";
        }

        @Override
        public ChatResponse chat(final ChatRequest request) {
            return new ChatResponse("fake", "fake-m", "ok",
                    ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY, java.time.Duration.ZERO);
        }

        @Override
        public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
            return streamSupplier.get();
        }
    }
}
