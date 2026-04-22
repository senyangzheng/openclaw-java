package com.openclaw.providers.api;

import java.util.Objects;

/**
 * Event carried on {@code Flux<ChatChunkEvent>} returned from
 * {@link ProviderClient#streamChat(ChatRequest)}. Sealed so downstream
 * consumers can pattern-match exhaustively in switch expressions.
 *
 * <p>Typical emission order:
 * <pre>
 *   Delta("I")  Delta(" think")  Delta(" it is 42.")  Done(STOP, usage)
 * </pre>
 * A stream terminates with exactly one of {@link Done} or {@link Error};
 * downstream should treat the {@code Flux} completion signal as implicit
 * {@link Done#STOP} if neither was observed (best-effort providers).
 */
public sealed interface ChatChunkEvent {

    /**
     * A piece of assistant-authored text. Empty content is allowed (some providers
     * send an empty leading delta to announce the role/model).
     */
    record Delta(String content) implements ChatChunkEvent {
        public Delta {
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * An incremental tool call payload. Wraps {@link ToolCallChunk} to keep the
     * sealed hierarchy flat.
     */
    record ToolCall(ToolCallChunk chunk) implements ChatChunkEvent {
        public ToolCall {
            Objects.requireNonNull(chunk, "chunk");
        }
    }

    /**
     * Terminal success event. Always the last event on a successful stream.
     */
    record Done(ChatResponse.FinishReason reason, ChatResponse.Usage usage) implements ChatChunkEvent {
        public Done {
            reason = reason == null ? ChatResponse.FinishReason.STOP : reason;
            usage = usage == null ? ChatResponse.Usage.EMPTY : usage;
        }

        public static final Done STOP = new Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY);
    }

    /**
     * Terminal error event. Emitted as a regular {@code onNext} so consumers can
     * decide whether to propagate as an exception or keep the stream open. Providers
     * also remain free to signal fatal errors via {@code Flux.error(...)}.
     */
    record Error(String code, String message) implements ChatChunkEvent {
        public Error {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
