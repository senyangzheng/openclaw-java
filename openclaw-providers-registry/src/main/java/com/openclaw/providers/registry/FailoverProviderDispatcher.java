package com.openclaw.providers.registry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Reference {@link ProviderDispatcher} implementation. Walks the registry's preferred order skipping
 * cooling-down providers; on per-call failure, classifies the exception and either moves on to the next
 * candidate or rethrows for non-retryable reasons. Reports success / failure back to the registry so the
 * next caller sees an updated health view.
 *
 * <h2>Attempts record</h2>
 * Every attempt — including cooldown-skips — is appended to an {@link FailoverError.Attempt} list. On total
 * exhaustion the list is attached to a {@link FailoverError} so the caller can log structured
 * {@code providers.fallback.exhausted attempts=...} and emit per-reason metrics.
 *
 * <h2>Abort / auth semantics</h2>
 * {@link FailoverReason#ABORTED} always short-circuits (no retries, no cooldown update) — wrapping it in
 * {@link FailoverError} would hide the caller intent. {@link FailoverReason#AUTH} also short-circuits on the
 * current provider because we do not know how to rotate credentials here; a future
 * {@code AuthProfileRotator} (M3.9) can retry on a different profile before giving up.
 */
public class FailoverProviderDispatcher implements ProviderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(FailoverProviderDispatcher.class);

    private final ProviderRegistry registry;
    private final Clock clock;

    public FailoverProviderDispatcher(final ProviderRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    public FailoverProviderDispatcher(final ProviderRegistry registry, final Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ChatResponse chat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final List<String> ids = registry.providerIds();
        if (ids.isEmpty()) {
            throw new FailoverError(List.of(), noProviderError());
        }
        final List<FailoverError.Attempt> attempts = new ArrayList<>();
        RuntimeException lastError = null;

        for (final String id : ids) {
            if (registry.isCoolingDown(id)) {
                attempts.add(new FailoverError.Attempt(
                        id, FailoverReason.COOLDOWN, "skipped (cooling down)", clock.instant()));
                log.debug("providers.dispatch.skip.cooldown providerId={}", id);
                continue;
            }
            final ProviderClient client = registry.get(id).orElse(null);
            if (client == null) {
                continue;
            }
            try {
                final ChatResponse response = client.chat(request);
                registry.reportSuccess(id);
                return response;
            } catch (RuntimeException ex) {
                final FailoverReason reason = FailoverReasonClassifier.classify(ex);
                attempts.add(new FailoverError.Attempt(
                        id, reason, messageOf(ex), clock.instant()));
                if (reason == FailoverReason.ABORTED) {
                    log.info("providers.dispatch.aborted providerId={} reason=caller", id);
                    throw ex;
                }
                registry.reportFailure(id, ex);
                log.warn("providers.dispatch.failure providerId={} reason={} msg={}",
                        id, reason.code(), messageOf(ex));
                lastError = ex;
                if (!reason.retryable()) {
                    log.warn("providers.dispatch.non-retryable providerId={} reason={} — not falling over",
                            id, reason.code());
                    throw new FailoverError(List.copyOf(attempts), ex);
                }
            }
        }
        log.warn("providers.dispatch.exhausted attempts={}", summarize(attempts));
        throw new FailoverError(List.copyOf(attempts), lastError);
    }

    @Override
    public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final List<String> ids = registry.providerIds();
        if (ids.isEmpty()) {
            return Flux.error(new FailoverError(List.of(), noProviderError()));
        }
        return attemptStream(request, ids, 0, new ArrayList<>(), null);
    }

    private Flux<ChatChunkEvent> attemptStream(final ChatRequest request,
                                               final List<String> ids,
                                               final int index,
                                               final List<FailoverError.Attempt> attempts,
                                               final Throwable carriedError) {
        if (index >= ids.size()) {
            log.warn("providers.dispatch.stream.exhausted attempts={}", summarize(attempts));
            return Flux.error(new FailoverError(List.copyOf(attempts), carriedError));
        }
        final String id = ids.get(index);
        if (registry.isCoolingDown(id)) {
            attempts.add(new FailoverError.Attempt(
                    id, FailoverReason.COOLDOWN, "skipped (cooling down)", clock.instant()));
            log.debug("providers.dispatch.stream.skip.cooldown providerId={}", id);
            return attemptStream(request, ids, index + 1, attempts, carriedError);
        }
        final ProviderClient client = registry.get(id).orElse(null);
        if (client == null) {
            return attemptStream(request, ids, index + 1, attempts, carriedError);
        }
        return Flux.defer(() -> client.streamChat(request))
                .doOnComplete(() -> registry.reportSuccess(id))
                .onErrorResume(err -> {
                    final FailoverReason reason = FailoverReasonClassifier.classify(err);
                    attempts.add(new FailoverError.Attempt(id, reason, messageOf(err), clock.instant()));
                    if (reason == FailoverReason.ABORTED) {
                        return Flux.error(err);
                    }
                    registry.reportFailure(id, err);
                    log.warn("providers.dispatch.stream.failure providerId={} reason={} msg={}",
                            id, reason.code(), messageOf(err));
                    if (!reason.retryable()) {
                        return Flux.error(new FailoverError(List.copyOf(attempts), err));
                    }
                    return attemptStream(request, ids, index + 1, attempts, err);
                });
    }

    private static RuntimeException noProviderError() {
        return new com.openclaw.common.error.OpenClawException(
                ProviderRegistryErrorCode.NO_PROVIDER_REGISTERED);
    }

    private static String messageOf(final Throwable t) {
        if (t == null) {
            return "";
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    private static String summarize(final List<FailoverError.Attempt> attempts) {
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attempts.size(); i++) {
            final var a = attempts.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(a.providerId()).append(':').append(a.reason().code());
        }
        return sb.append(']').toString();
    }
}
