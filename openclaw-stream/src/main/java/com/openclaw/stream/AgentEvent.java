package com.openclaw.stream;

import java.util.Map;
import java.util.Objects;

import com.openclaw.providers.api.ChatResponse;

/**
 * Business-layer agent event. Decouples the upper-level agent runtime (PiAgentRunner / AutoReply / WebSocket SSE)
 * from the raw provider chunk format ({@code ChatChunkEvent}).
 *
 * <p>Mirrors openclaw-ts {@code src/agents/attempt/events.ts} {@code AttemptEvent} sealed union.
 *
 * <p><b>Canonical emission order</b> on a successful run:
 * <pre>
 *   Reasoning*  Delta*  (ToolCall  ToolResult)*  Delta*  Done
 * </pre>
 * <p>A stream always terminates with exactly one {@link Done} or {@link Error} — downstream consumers (SSE
 * encoder, session accumulator) rely on this invariant.
 */
public sealed interface AgentEvent permits
        AgentEvent.Delta,
        AgentEvent.Reasoning,
        AgentEvent.ToolCall,
        AgentEvent.ToolResult,
        AgentEvent.Done,
        AgentEvent.Error {

    /** A token-level chunk of the assistant's user-facing reply. */
    record Delta(String content) implements AgentEvent {
        public Delta {
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * A reasoning / "thinking" fragment emitted by providers that expose it separately (e.g. Qwen thinking mode,
     * Gemini "thought" steps). Usually hidden from UI but logged for diagnostics.
     */
    record Reasoning(String content) implements AgentEvent {
        public Reasoning {
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * A (possibly partial) tool-call issued by the model. {@code argumentsPartial} is the accumulated JSON up to
     * this point — consumers should render / act on it only once {@code complete == true}, or handle partial
     * JSON explicitly.
     *
     * @param id               provider-assigned call id (stable across partial chunks of the same call)
     * @param index            position in the tool-calls array (0-based)
     * @param name             function name; null until the provider has announced it
     * @param argumentsPartial JSON arguments accumulated so far
     * @param complete         {@code true} on the final chunk for this call (arguments fully received)
     */
    record ToolCall(String id,
                    int index,
                    String name,
                    String argumentsPartial,
                    boolean complete) implements AgentEvent {
        public ToolCall {
            Objects.requireNonNull(id, "id");
            argumentsPartial = argumentsPartial == null ? "" : argumentsPartial;
        }
    }

    /**
     * Structured tool-call result, produced by the Tool runtime after executing a {@link ToolCall}.
     * Downstream consumers may re-emit this to the UI (for tool-call audit logs) or fold it back into the next
     * provider turn.
     *
     * @param callId   matches the originating {@link ToolCall#id()}
     * @param name     tool name (repeated for convenience; downstream may have dropped the ToolCall record)
     * @param ok       {@code true} for successful execution, {@code false} for errors
     * @param output   raw output payload (tool-specific; for JSON tools, the canonical JSON string)
     * @param metadata optional tags (e.g. {@code durationMs}, {@code cacheHit}); never null
     */
    record ToolResult(String callId,
                      String name,
                      boolean ok,
                      String output,
                      Map<String, Object> metadata) implements AgentEvent {
        public ToolResult {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(name, "name");
            output = output == null ? "" : output;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /** Terminal success event. */
    record Done(ChatResponse.FinishReason reason, ChatResponse.Usage usage) implements AgentEvent {
        public Done {
            reason = reason == null ? ChatResponse.FinishReason.STOP : reason;
            usage = usage == null ? ChatResponse.Usage.EMPTY : usage;
        }

        public static final Done STOP = new Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY);
    }

    /**
     * Terminal error event. Emitted when a stream fails — the upstream {@code Flux} also terminates via
     * {@code onError}, but this event gives clients a structured code+message pair before that signal.
     */
    record Error(String code, String message) implements AgentEvent {
        public Error {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
