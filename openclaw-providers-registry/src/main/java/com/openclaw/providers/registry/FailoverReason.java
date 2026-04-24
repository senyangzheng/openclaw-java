package com.openclaw.providers.registry;

/**
 * Canonical failover reason vocabulary. Mirrors the openclaw-ts classifier output — keeping the strings
 * stable is important because downstream log/metrics consumers (Prometheus labels, log-based alerts) query
 * by the enum's {@link #code()}.
 *
 * <ul>
 *   <li>{@link #TIMEOUT} — provider exceeded the configured deadline</li>
 *   <li>{@link #RATE_LIMIT} — upstream signalled 429 / quota exhaustion</li>
 *   <li>{@link #AUTH} — 401 / 403 / invalid api-key — <b>non-retryable</b> on the same credential</li>
 *   <li>{@link #SERVER_ERROR} — 5xx; retryable</li>
 *   <li>{@link #CLIENT_ERROR} — 4xx other than 401/403/429; usually non-retryable</li>
 *   <li>{@link #NETWORK} — DNS / connection reset / socket-level failure; retryable</li>
 *   <li>{@link #COOLDOWN} — provider is on the registry's cooldown list; attempted skipped</li>
 *   <li>{@link #ABORTED} — caller-side abort (signal / cancelled); must NOT trigger fallback</li>
 *   <li>{@link #UNKNOWN} — anything else</li>
 * </ul>
 */
public enum FailoverReason {

    TIMEOUT("timeout", true),
    RATE_LIMIT("rate_limit", true),
    AUTH("auth", false),
    SERVER_ERROR("server_error", true),
    CLIENT_ERROR("client_error", false),
    NETWORK("network", true),
    COOLDOWN("cooldown", true),
    ABORTED("aborted", false),
    UNKNOWN("unknown", true);

    private final String code;
    private final boolean retryable;

    FailoverReason(final String code, final boolean retryable) {
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    /**
     * @return {@code true} if this reason should cause the dispatcher to try the next provider candidate;
     *         {@code false} for abort / auth / deterministic 4xx where retrying on another provider is either
     *         impossible ({@link #ABORTED}) or futile ({@link #CLIENT_ERROR}).
     */
    public boolean retryable() {
        return retryable;
    }
}
