package com.openclaw.agents.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.openclaw.providers.api.ChatResponse;
import com.openclaw.stream.AgentEvent;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class SubscribeStateTest {

    @Test
    void emitsFoldAndPublishToSubscribers() {
        final SubscribeState state = new SubscribeState();
        final var flux = state.subscribe();
        final var verifier = StepVerifier.create(flux)
                .then(() -> {
                    state.emit(new AgentEvent.Delta("hi"));
                    state.emit(new AgentEvent.Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY));
                    state.complete();
                })
                .expectNextMatches(e -> e instanceof AgentEvent.Delta d && d.content().equals("hi"))
                .expectNextMatches(e -> e instanceof AgentEvent.Done)
                .verifyComplete();
        assertThat(verifier).isNotNull();
        assertThat(state.snapshot().assistantText()).isEqualTo("hi");
    }

    @Test
    void peekersFireSynchronouslyBeforeFlux() {
        final SubscribeState state = new SubscribeState();
        final List<AgentEvent> observed = new CopyOnWriteArrayList<>();
        state.peek(observed::add);
        state.emit(new AgentEvent.Delta("x"));
        assertThat(observed).hasSize(1);
    }

    @Test
    void emitAfterTerminationIsNoOp() {
        final SubscribeState state = new SubscribeState();
        state.complete();
        state.emit(new AgentEvent.Delta("late"));
        assertThat(state.snapshot().assistantText()).isEmpty();
    }

    @Test
    void errorPathTerminatesStream() {
        final SubscribeState state = new SubscribeState();
        final var flux = state.subscribe();
        StepVerifier.create(flux)
                .then(() -> {
                    state.emit(new AgentEvent.Delta("a"));
                    state.error(new RuntimeException("boom"));
                })
                .expectNextMatches(e -> e instanceof AgentEvent.Delta)
                .verifyError(RuntimeException.class);
    }
}
