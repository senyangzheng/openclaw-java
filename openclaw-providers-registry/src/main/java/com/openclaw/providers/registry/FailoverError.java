package com.openclaw.providers.registry;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.openclaw.common.error.OpenClawException;

/**
 * Thrown by {@link ProviderDispatcher} when every preferred provider candidate either failed or was skipped
 * on cooldown.
 *
 * <p>Exposes the full {@link Attempt attempts list} so observability hooks can emit a structured
 * {@code providers.fallback.exhausted} event. {@link Throwable#getCause()} points at the <i>last</i>
 * observed provider error (the one that finally exhausted the chain) to keep stack traces useful, while
 * {@link #attempts()} preserves the chronological record of every provider tried.
 *
 * <p>Mirrors the openclaw-ts {@code FailoverError} shape (see docs 09 · provider-failover).
 */
public final class FailoverError extends OpenClawException {

    private static final long serialVersionUID = 1L;

    private final List<Attempt> attempts;

    public FailoverError(final List<Attempt> attempts, final Throwable lastCause) {
        super(ProviderRegistryErrorCode.ALL_PROVIDERS_EXHAUSTED, buildMessage(attempts), lastCause);
        this.attempts = List.copyOf(Objects.requireNonNullElse(attempts, List.of()));
    }

    public List<Attempt> attempts() {
        return attempts;
    }

    private static String buildMessage(final List<Attempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return "all providers exhausted (no attempts recorded)";
        }
        final StringBuilder sb = new StringBuilder("all providers exhausted: ");
        for (int i = 0; i < attempts.size(); i++) {
            final Attempt a = attempts.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a.providerId()).append('=').append(a.reason().code());
        }
        return sb.toString();
    }

    /**
     * One recorded attempt against a provider.
     *
     * @param providerId provider being attempted
     * @param reason     classified reason the attempt failed (or {@link FailoverReason#COOLDOWN} when skipped)
     * @param message    original error message (may be {@code null} when skipped for cooldown)
     * @param at         wall-clock timestamp of the attempt
     */
    public record Attempt(String providerId,
                          FailoverReason reason,
                          String message,
                          Instant at) {

        public Attempt {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(at, "at");
        }
    }
}
