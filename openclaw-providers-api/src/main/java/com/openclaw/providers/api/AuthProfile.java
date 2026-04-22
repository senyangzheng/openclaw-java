package com.openclaw.providers.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single credential slot that a provider pool can lease. Identified by
 * {@code profileId} within its {@code providerId}. {@code apiKey} MUST be
 * sourced from {@code openclaw-secrets} (or an environment variable) — never
 * committed to git. {@code extras} carries provider-specific hints
 * (e.g. {@code regionId}, {@code endpointOverride}, quota tier).
 */
public record AuthProfile(
    String profileId,
    String providerId,
    String apiKey,
    Map<String, String> extras
) {

    public AuthProfile {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(apiKey, "apiKey");
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    public static AuthProfile of(final String profileId, final String providerId, final String apiKey) {
        return new AuthProfile(profileId, providerId, apiKey, Map.of());
    }

    /**
     * @return the value of an {@code extras} entry or {@code null} if absent.
     */
    public String extra(final String key) {
        return extras.get(key);
    }

    /**
     * @return a new profile whose extras are the union of existing + given pairs
     *         (given pairs win on conflict).
     */
    public AuthProfile withExtras(final Map<String, String> more) {
        final Map<String, String> merged = new LinkedHashMap<>(extras);
        if (more != null) {
            merged.putAll(more);
        }
        return new AuthProfile(profileId, providerId, apiKey, merged);
    }
}
