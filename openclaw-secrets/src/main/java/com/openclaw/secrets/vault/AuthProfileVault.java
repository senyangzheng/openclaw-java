package com.openclaw.secrets.vault;

import com.openclaw.providers.api.AuthProfile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read / write store for {@link AuthProfile}s. An {@code AuthProfile} bundles
 * {@code apiKey} (always envelope-encrypted at rest) plus arbitrary {@code extras}
 * such as region or quota tier.
 *
 * <p><b>Lookup strategy</b> (see {@link #findFirst(String)}): the first profile
 * registered under a given {@code providerId} wins — sufficient for the single-key
 * case today. A later pooling layer can extend this via {@link #listByProvider(String)}.
 *
 * <p>Implementations MUST:
 * <ul>
 *   <li>Never log or expose {@code apiKey} outside of a returned {@link AuthProfile};</li>
 *   <li>Return {@link Optional#empty()} for unknown providers — never throw;</li>
 *   <li>Be thread-safe — {@code openclaw-providers-registry} may call from any thread.</li>
 * </ul>
 */
public interface AuthProfileVault {

    /** Stable short identifier, e.g. {@code "mem"}, {@code "jdbc"}. Useful for
     * diagnostics / log correlation. */
    String source();

    /** Look up a specific profile by (providerId, profileId). */
    Optional<AuthProfile> find(String providerId, String profileId);

    /** Return the first profile registered under {@code providerId}, if any. */
    default Optional<AuthProfile> findFirst(final String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        final List<AuthProfile> all = listByProvider(providerId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /** List every profile registered under {@code providerId}. Order is stable
     * per implementation (JDBC: creation order via id ascending). */
    List<AuthProfile> listByProvider(String providerId);

    /**
     * Upsert {@code profile}, encrypting the apiKey on storage.
     *
     * <p>Implementations that are read-only (e.g. an env-backed adapter) MAY throw
     * {@link UnsupportedOperationException}; callers that need write semantics
     * should check {@link #supportsWrites()} first.
     */
    default void save(final AuthProfile profile) {
        throw new UnsupportedOperationException(source() + " is a read-only vault");
    }

    /** @return {@code true} when {@link #save(AuthProfile)} is supported. */
    default boolean supportsWrites() {
        return false;
    }

    /** Remove a profile. No-op when not present. */
    default void delete(final String providerId, final String profileId) {
        throw new UnsupportedOperationException(source() + " is a read-only vault");
    }
}
