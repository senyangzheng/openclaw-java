package com.openclaw.stream;

import java.util.HashMap;
import java.util.Map;

import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ToolCallChunk;

import reactor.core.publisher.Flux;

/**
 * Translate the provider-layer {@link ChatChunkEvent} stream into business-layer {@link AgentEvent} stream.
 *
 * <p>Mapping:
 * <table>
 *   <caption>translation table</caption>
 *   <tr><th>source</th><th>target</th></tr>
 *   <tr><td>{@link ChatChunkEvent.Delta}</td><td>{@link AgentEvent.Delta}</td></tr>
 *   <tr><td>{@link ChatChunkEvent.ToolCall}</td><td>{@link AgentEvent.ToolCall} (accumulating JSON args)</td></tr>
 *   <tr><td>{@link ChatChunkEvent.Done}</td><td>{@link AgentEvent.Done}</td></tr>
 *   <tr><td>{@link ChatChunkEvent.Error}</td><td>{@link AgentEvent.Error}</td></tr>
 * </table>
 *
 * <p>{@link AgentEvent.Reasoning} and {@link AgentEvent.ToolResult} are NOT produced here — they are emitted
 * by the agent runtime (agent-core / tool-runtime) after the translation step.
 *
 * <p><b>Tool-call argument accumulation</b>: OpenAI / Qwen emit tool-call JSON in fragments keyed by
 * {@code ToolCallChunk.id}. The translator buffers these fragments and re-emits an {@link AgentEvent.ToolCall}
 * on every chunk with {@code complete=false}. When the upstream emits a {@link ChatChunkEvent.Done}, any still
 * un-completed tool calls are re-emitted once more with {@code complete=true} <b>before</b> the {@link Done}.
 */
public final class ChatChunkEventTranslator {

    private ChatChunkEventTranslator() {
    }

    /**
     * Stateless per-element translation, suitable when tool-call argument accumulation is handled elsewhere
     * (the agent runtime keeps a {@code ToolCallBuffer}). Preserves 1:1 emission count.
     */
    public static AgentEvent translateOne(final ChatChunkEvent in) {
        if (in == null) {
            throw new IllegalArgumentException("chunk is required");
        }
        return switch (in) {
            case ChatChunkEvent.Delta d -> new AgentEvent.Delta(d.content());
            case ChatChunkEvent.ToolCall tc -> toAgentToolCall(tc.chunk(), "", false);
            case ChatChunkEvent.Done d -> new AgentEvent.Done(d.reason(), d.usage());
            case ChatChunkEvent.Error e -> new AgentEvent.Error(e.code(), e.message());
        };
    }

    /**
     * Stateful {@link Flux} transform that maintains a per-call JSON-arguments buffer and emits one
     * {@link AgentEvent.ToolCall} with {@code complete=true} when the stream terminates.
     *
     * <p>The returned {@code Flux} is cold and may be subscribed to at most once (state is held in the closure).
     */
    public static Flux<AgentEvent> translateFlux(final Flux<ChatChunkEvent> upstream) {
        if (upstream == null) {
            throw new IllegalArgumentException("upstream is required");
        }
        // stateful buffers, keyed by tool-call id
        final Map<String, ToolCallBuffer> buffers = new HashMap<>();

        return upstream
                .concatMap(chunk -> switch (chunk) {
                    case ChatChunkEvent.Delta d -> Flux.just(new AgentEvent.Delta(d.content()));
                    case ChatChunkEvent.ToolCall tc -> {
                        final ToolCallBuffer buf = buffers.computeIfAbsent(tc.chunk().id(),
                                id -> new ToolCallBuffer(id, tc.chunk().index()));
                        buf.append(tc.chunk());
                        yield Flux.just(buf.snapshot(false));
                    }
                    case ChatChunkEvent.Done d -> {
                        // flush partial tool calls as complete before the terminal event
                        final java.util.List<AgentEvent> out = new java.util.ArrayList<>();
                        for (final ToolCallBuffer buf : buffers.values()) {
                            if (!buf.flushed) {
                                out.add(buf.snapshot(true));
                                buf.flushed = true;
                            }
                        }
                        out.add(new AgentEvent.Done(d.reason(), d.usage()));
                        yield Flux.fromIterable(out);
                    }
                    case ChatChunkEvent.Error e ->
                            Flux.just(new AgentEvent.Error(e.code(), e.message()));
                });
    }

    private static AgentEvent.ToolCall toAgentToolCall(final ToolCallChunk c,
                                                       final String args,
                                                       final boolean complete) {
        return new AgentEvent.ToolCall(c.id(), c.index(), c.name(), args == null ? "" : args, complete);
    }

    // package-private: in-progress tool-call JSON accumulator
    private static final class ToolCallBuffer {
        final String id;
        final int index;
        String name;
        final StringBuilder args = new StringBuilder();
        boolean flushed;

        ToolCallBuffer(final String id, final int index) {
            this.id = id;
            this.index = index;
        }

        void append(final ToolCallChunk chunk) {
            if (chunk.name() != null && this.name == null) {
                this.name = chunk.name();
            }
            if (!chunk.argumentsDelta().isEmpty()) {
                this.args.append(chunk.argumentsDelta());
            }
        }

        AgentEvent.ToolCall snapshot(final boolean complete) {
            return new AgentEvent.ToolCall(id, index, name, args.toString(), complete);
        }
    }
}
