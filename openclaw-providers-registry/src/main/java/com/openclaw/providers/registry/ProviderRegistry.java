package com.openclaw.providers.registry;

import com.openclaw.providers.api.AuthProfile;
import com.openclaw.providers.api.ProviderClient;

import java.util.List;
import java.util.Optional;

/**
 * Collection of {@link ProviderClient}s with health / cooldown tracking. The
 * registry is the single source of truth for multi-provider routing — downstream
 * consumers (pipelines, tools) interact with it through either
 * {@link #select()} or the {@code @Primary} {@link CompositeProviderClient}
 * bean that wraps it.
 */
public interface ProviderRegistry {

    /**
     * @return all registered provider ids, in the configured fallback order
     *         (preferred first).
     */
    List<String> providerIds();

    /**
     * @return the first non-cooling-down provider in the fallback order, or
     *         {@link Optional#empty()} when every provider is on cooldown.
     */
    Optional<ProviderClient> select();

    /**
     * @return the provider with the given id regardless of cooldown state, or
     *         {@link Optional#empty()} when unknown.
     */
    Optional<ProviderClient> get(String providerId);

    /**
     * @return {@code true} if the given provider is currently cooling down.
     */
    boolean isCoolingDown(String providerId);

    /**
     * Report a failure against the given provider. Puts it on cooldown using the
     * configured {@link com.openclaw.providers.api.CooldownPolicy} and the current
     * consecutive-failure count.
     */
    void reportFailure(String providerId, Throwable error);

    /**
     * Report a successful call, resetting the consecutive-failure count and
     * clearing any active cooldown.
     */
    void reportSuccess(String providerId);

    /**
     * Resolve the first {@link AuthProfile} registered for the given provider,
     * consulting the wired {@code AuthProfileVault} (see
     * {@code openclaw-secrets}). Returns {@link Optional#empty()} when the vault
     * has no entry or no vault is configured — callers then fall back to the
     * provider-properties {@code apiKey}.
     */
    default Optional<AuthProfile> authProfile(final String providerId) {
        return Optional.empty();
    }
}
