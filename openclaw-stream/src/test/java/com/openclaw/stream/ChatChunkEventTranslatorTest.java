package com.openclaw.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ToolCallChunk;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ChatChunkEventTranslatorTest {

    @Test
    void translateOne_mapsEachChunkKind() {
        assertThat(ChatChunkEventTranslator.translateOne(new ChatChunkEvent.Delta("hi")))
                .isInstanceOf(AgentEvent.Delta.class);
        assertThat(ChatChunkEventTranslator.translateOne(
                new ChatChunkEvent.ToolCall(new ToolCallChunk("c1", 0, "f", ""))))
                .isInstanceOf(AgentEvent.ToolCall.class);
        assertThat(ChatChunkEventTranslator.translateOne(
                new ChatChunkEvent.Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY)))
                .isInstanceOf(AgentEvent.Done.class);
        assertThat(ChatChunkEventTranslator.translateOne(new ChatChunkEvent.Error("E_X", "bad")))
                .isInstanceOf(AgentEvent.Error.class);
    }

    @Test
    void translateFlux_emitsDeltasPassThrough() {
        final Flux<AgentEvent> out = ChatChunkEventTranslator.translateFlux(Flux.just(
                new ChatChunkEvent.Delta("hel"),
                new ChatChunkEvent.Delta("lo"),
                ChatChunkEvent.Done.STOP));

        StepVerifier.create(out)
                .expectNextMatches(e -> e instanceof AgentEvent.Delta d && d.content().equals("hel"))
                .expectNextMatches(e -> e instanceof AgentEvent.Delta d && d.content().equals("lo"))
                .expectNextMatches(e -> e instanceof AgentEvent.Done)
                .verifyComplete();
    }

    @Test
    void translateFlux_accumulatesToolCallArgsAndFlushesCompleteBeforeDone() {
        final Flux<ChatChunkEvent> source = Flux.just(
                new ChatChunkEvent.ToolCall(new ToolCallChunk("tc1", 0, "weather", "{\"c\":")),
                new ChatChunkEvent.ToolCall(new ToolCallChunk("tc1", 0, null, "\"Seattle\"}")),
                new ChatChunkEvent.Done(ChatResponse.FinishReason.TOOL_CALLS, ChatResponse.Usage.EMPTY));

        final List<AgentEvent> events = ChatChunkEventTranslator.translateFlux(source).collectList().block();
        assertThat(events).hasSize(4);

        final AgentEvent.ToolCall first = (AgentEvent.ToolCall) events.get(0);
        assertThat(first.name()).isEqualTo("weather");
        assertThat(first.argumentsPartial()).isEqualTo("{\"c\":");
        assertThat(first.complete()).isFalse();

        final AgentEvent.ToolCall second = (AgentEvent.ToolCall) events.get(1);
        assertThat(second.argumentsPartial()).isEqualTo("{\"c\":\"Seattle\"}");
        assertThat(second.complete()).isFalse();

        final AgentEvent.ToolCall flushed = (AgentEvent.ToolCall) events.get(2);
        assertThat(flushed.argumentsPartial()).isEqualTo("{\"c\":\"Seattle\"}");
        assertThat(flushed.complete()).isTrue();

        assertThat(events.get(3)).isInstanceOf(AgentEvent.Done.class);
    }

    @Test
    void translateFlux_preservesErrorEvent() {
        final Flux<AgentEvent> out = ChatChunkEventTranslator.translateFlux(Flux.just(
                new ChatChunkEvent.Delta("part"),
                new ChatChunkEvent.Error("E_RATE", "rate limited")));

        StepVerifier.create(out)
                .expectNextMatches(e -> e instanceof AgentEvent.Delta)
                .expectNextMatches(e -> e instanceof AgentEvent.Error err
                        && err.code().equals("E_RATE")
                        && err.message().equals("rate limited"))
                .verifyComplete();
    }
}
