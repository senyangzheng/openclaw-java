package com.openclaw.providers.registry;

import java.util.Objects;

import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;

import reactor.core.publisher.Flux;

/**
 * Upstream-facing chat entry point. Wraps the {@link ProviderRegistry} with a failover strategy and reports
 * per-provider success/failure back to the registry's cooldown tracker.
 *
 * <p><b>Why not extend {@link com.openclaw.providers.api.ProviderClient}?</b> Earlier milestones registered
 * a {@code CompositeProviderClient} bean with {@code @Primary}; this conflated two responsibilities (a single
 * model client vs. a dispatcher over many) and forced the registry autoconfig to filter itself out of its own
 * member list to avoid Spring cycles. M3 / A4 splits the two: provider SDKs still implement
 * {@code ProviderClient}, but everything upstream (pipeline, agent attempt executor) depends on
 * {@code ProviderDispatcher} instead. See {@code .cursor/plan/02-maven-modules.md}.
 *
 * <p>Failure contract:
 * <ul>
 *   <li>Each attempt is classified with {@link FailoverReasonClassifier}; retryable reasons move on to the
 *       next candidate, {@link FailoverReason#ABORTED} / {@link FailoverReason#AUTH} / deterministic
 *       {@link FailoverReason#CLIENT_ERROR} are rethrown immediately.</li>
 *   <li>When every candidate is exhausted, a {@link FailoverError} is thrown with the full
 *       {@link FailoverError.Attempt} list for observability.</li>
 * </ul>
 */
public interface ProviderDispatcher {

    /** Single-shot chat. */
    ChatResponse chat(ChatRequest request);

    /** Streaming chat. The returned flux terminates either with a {@link ChatChunkEvent.Done} or an error. */
    Flux<ChatChunkEvent> streamChat(ChatRequest request);

    /**
     * Thin dispatcher that delegates to a single {@link ProviderClient} with no failover, no cooldown.
     * Use this in tests or in the rare deployments that explicitly bind to one supplier.
     * Production code should rely on the Spring-registered {@link FailoverProviderDispatcher} driven by
     * {@link ProviderRegistry}.
     */
    static ProviderDispatcher direct(final ProviderClient client) {
        Objects.requireNonNull(client, "client");
        return new ProviderDispatcher() {
            @Override
            public ChatResponse chat(final ChatRequest request) {
                return client.chat(request);
            }

            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                return client.streamChat(request);
            }
        };
    }
}
