package com.openclaw.providers.api;

import java.time.Duration;

/**
 * Exponential-backoff cooldown used by the provider registry when a client (or
 * auth profile) reports a failure.
 *
 * <pre>
 *   attempt 1 → initialDelay
 *   attempt 2 → initialDelay * multiplier
 *   ...
 *   attempt N → min(initialDelay * multiplier^(N-1), maxDelay)
 * </pre>
 *
 * @param initialDelay cooldown after the first failure (must be &gt; 0)
 * @param maxDelay     upper cap — subsequent failures never exceed this (must be ≥ initialDelay)
 * @param multiplier   backoff factor applied on repeated failures (must be ≥ 1.0)
 */
public record CooldownPolicy(Duration initialDelay, Duration maxDelay, double multiplier) {

    public static final CooldownPolicy DEFAULT = new CooldownPolicy(
        Duration.ofSeconds(5), Duration.ofMinutes(5), 2.0);

    public CooldownPolicy {
        if (initialDelay == null || initialDelay.isZero() || initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must be > 0");
        }
        if (maxDelay == null || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= initialDelay");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
    }

    /**
     * @param attempt 1-based consecutive failure counter
     * @return cooldown duration for the given attempt, capped at {@link #maxDelay}
     */
    public Duration delayForAttempt(final int attempt) {
        if (attempt < 1) {
            return Duration.ZERO;
        }
        final double factor = Math.pow(multiplier, attempt - 1);
        final long millis = Math.min(
            maxDelay.toMillis(),
            (long) Math.min(initialDelay.toMillis() * factor, Long.MAX_VALUE));
        return Duration.ofMillis(millis);
    }
}
