package com.openclaw.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.openclaw.providers.api.ChatResponse;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class AgentEventSinkTest {

    @Test
    void accumulatesDeltasIntoAssistantText() {
        final AgentEventSink sink = new AgentEventSink();
        sink.accept(new AgentEvent.Delta("hel"))
                .accept(new AgentEvent.Delta("lo"))
                .accept(new AgentEvent.Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY));
        assertThat(sink.assistantText()).isEqualTo("hello");
        assertThat(sink.isTerminated()).isTrue();
        assertThat(sink.finishReason()).hasValue(ChatResponse.FinishReason.STOP);
    }

    @Test
    void reasoningIsSeparateFromAssistantText() {
        final AgentEventSink sink = new AgentEventSink();
        sink.accept(new AgentEvent.Reasoning("thinking..."));
        sink.accept(new AgentEvent.Delta("answer"));
        assertThat(sink.assistantText()).isEqualTo("answer");
        assertThat(sink.reasoningText()).isEqualTo("thinking...");
    }

    @Test
    void toolCallsLastWriterWinsPerId() {
        final AgentEventSink sink = new AgentEventSink();
        sink.accept(new AgentEvent.ToolCall("tc1", 0, "f", "{\"a\"", false));
        sink.accept(new AgentEvent.ToolCall("tc1", 0, "f", "{\"a\":1}", true));
        assertThat(sink.toolCalls()).hasSize(1);
        final AgentEvent.ToolCall last = sink.toolCalls().get(0);
        assertThat(last.complete()).isTrue();
        assertThat(last.argumentsPartial()).isEqualTo("{\"a\":1}");
    }

    @Test
    void errorTerminatesTheSinkBeforeDone() {
        final AgentEventSink sink = new AgentEventSink();
        sink.accept(new AgentEvent.Error("E", "boom"));
        assertThat(sink.isTerminated()).isTrue();
        assertThat(sink.error()).isPresent();
    }

    @Test
    void toolResultsAccumulated() {
        final AgentEventSink sink = new AgentEventSink();
        sink.accept(new AgentEvent.ToolResult("tc1", "f", true, "42", Map.of("durationMs", 12)));
        assertThat(sink.toolResults()).hasSize(1);
        assertThat(sink.toolResults().get(0).output()).isEqualTo("42");
    }

    @Test
    void foldBlocking_collectsEntireSnapshot() {
        final Flux<AgentEvent> flux = Flux.just(
                new AgentEvent.Delta("a"),
                new AgentEvent.Delta("b"),
                new AgentEvent.Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY));
        final AgentEventSink.Snapshot snap = AgentEventSink.foldBlocking(flux);
        assertThat(snap.assistantText()).isEqualTo("ab");
        assertThat(snap.finishReason()).isEqualTo(ChatResponse.FinishReason.STOP);
    }
}
