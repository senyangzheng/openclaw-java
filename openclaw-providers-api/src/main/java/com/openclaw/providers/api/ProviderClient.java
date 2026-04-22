package com.openclaw.providers.api;

import reactor.core.publisher.Flux;

/**
 * Minimal Provider SPI. Every concrete provider (Gemini, Qwen, Mock, ...) implements this.
 * <p>
 * Both blocking ({@link #chat(ChatRequest)}) and streaming
 * ({@link #streamChat(ChatRequest)}) modes are required by the contract, but
 * {@code streamChat} ships with a default that wraps the blocking result into
 * a single {@link ChatChunkEvent.Delta} + {@link ChatChunkEvent.Done} pair. That
 * keeps trivial providers (mocks, non-streaming backends) zero-boilerplate while
 * allowing real-time providers to override with native SSE / WebSocket delivery.
 */
public interface ProviderClient {

    /**
     * A short stable identifier ({@code "mock"}, {@code "google"}, {@code "qwen"}, ...),
     * used by the registry to pick a client and by logs / metrics to tag outbound calls.
     */
    String providerId();

    ChatResponse chat(ChatRequest request);

    /**
     * Streaming counterpart of {@link #chat(ChatRequest)}. The default simply
     * re-emits the blocking result as a {@code Delta} + {@code Done}, which is
     * sufficient for mocks and legacy providers. Real-time providers override
     * this to push token-level deltas.
     *
     * <p>Downstream semantics (enforced by integration tests, not the compiler):
     * <ul>
     *   <li>At most one terminal event ({@link ChatChunkEvent.Done} or
     *       {@link ChatChunkEvent.Error}) is emitted.</li>
     *   <li>The {@code Flux} always terminates — either via {@code onComplete}
     *       after the terminal event, or via {@code onError} for fatal failures.</li>
     * </ul>
     */
    default Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
        return Flux.defer(() -> {
            try {
                final ChatResponse response = chat(request);
                return Flux.just(
                    (ChatChunkEvent) new ChatChunkEvent.Delta(response.content()),
                    new ChatChunkEvent.Done(response.finishReason(), response.usage())
                );
            } catch (RuntimeException ex) {
                return Flux.error(ex);
            }
        });
    }
}
