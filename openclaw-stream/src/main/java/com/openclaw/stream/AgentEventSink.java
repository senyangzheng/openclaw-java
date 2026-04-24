package com.openclaw.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.openclaw.providers.api.ChatResponse;

import reactor.core.publisher.Flux;

/**
 * Downstream accumulator that folds an {@link AgentEvent} stream into a final snapshot.
 *
 * <p>Typical usage (session-persistence side-effect + propagation to SSE):
 * <pre>{@code
 * final AgentEventSink sink = new AgentEventSink();
 * return translatedFlux
 *     .doOnNext(sink::accept)
 *     .doOnComplete(() -> session.append(ChatMessage.assistant(sink.assistantText())));
 * }</pre>
 *
 * <p>Also supports a one-shot helper {@link #foldBlocking(Flux)} that collects a full snapshot.
 *
 * <p>Not thread-safe — use a per-stream instance.
 */
public final class AgentEventSink {

    private final StringBuilder assistant = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final List<AgentEvent.ToolCall> toolCalls = new ArrayList<>();
    private final Map<String, AgentEvent.ToolCall> toolCallById = new HashMap<>();
    private final List<AgentEvent.ToolResult> toolResults = new ArrayList<>();
    private ChatResponse.FinishReason finishReason;
    private ChatResponse.Usage usage;
    private AgentEvent.Error error;

    /** Consume a single event; return self for chaining. */
    public AgentEventSink accept(final AgentEvent event) {
        if (event == null) {
            return this;
        }
        switch (event) {
            case AgentEvent.Delta d -> assistant.append(d.content());
            case AgentEvent.Reasoning r -> reasoning.append(r.content());
            case AgentEvent.ToolCall tc -> {
                // Last-writer-wins per id so the final {@code complete=true} snapshot replaces earlier partials.
                toolCallById.put(tc.id(), tc);
            }
            case AgentEvent.ToolResult tr -> toolResults.add(tr);
            case AgentEvent.Done d -> {
                this.finishReason = d.reason();
                this.usage = d.usage();
            }
            case AgentEvent.Error e -> this.error = e;
        }
        return this;
    }

    public String assistantText() {
        return assistant.toString();
    }

    public String reasoningText() {
        return reasoning.toString();
    }

    /** Tool-calls observed in this stream, deduplicated by id (final state per id). */
    public List<AgentEvent.ToolCall> toolCalls() {
        if (toolCalls.isEmpty()) {
            // one-shot materialization preserves insertion order of ids
            toolCalls.addAll(toolCallById.values());
        }
        return Collections.unmodifiableList(toolCalls);
    }

    public List<AgentEvent.ToolResult> toolResults() {
        return Collections.unmodifiableList(toolResults);
    }

    public Optional<ChatResponse.FinishReason> finishReason() {
        return Optional.ofNullable(finishReason);
    }

    public Optional<ChatResponse.Usage> usage() {
        return Optional.ofNullable(usage);
    }

    public Optional<AgentEvent.Error> error() {
        return Optional.ofNullable(error);
    }

    public boolean isTerminated() {
        return finishReason != null || error != null;
    }

    /**
     * Snapshot record for convenience when callers don't need the sink's mutable reference.
     */
    public record Snapshot(String assistantText,
                           String reasoningText,
                           List<AgentEvent.ToolCall> toolCalls,
                           List<AgentEvent.ToolResult> toolResults,
                           ChatResponse.FinishReason finishReason,
                           ChatResponse.Usage usage,
                           AgentEvent.Error error) {
        public Snapshot {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        }

        public boolean isError() {
            return error != null;
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                assistantText(),
                reasoningText(),
                toolCalls(),
                toolResults(),
                finishReason,
                usage,
                error);
    }

    /**
     * Blocking helper: consume the whole flux, returning the final snapshot. Not for use in async paths;
     * prefer {@code .doOnNext(sink::accept)} for reactive composition.
     */
    public static Snapshot foldBlocking(final Flux<AgentEvent> flux) {
        final AgentEventSink sink = new AgentEventSink();
        flux.doOnNext(sink::accept).blockLast();
        return sink.snapshot();
    }
}
