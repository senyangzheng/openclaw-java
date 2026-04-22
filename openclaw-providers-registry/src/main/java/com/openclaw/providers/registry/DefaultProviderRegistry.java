package com.openclaw.providers.registry;

import com.openclaw.providers.api.AuthProfile;
import com.openclaw.providers.api.CooldownPolicy;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.secrets.vault.AuthProfileVault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory registry with per-provider cooldown tracking.
 *
 * <h4>Ordering</h4>
 * <ol>
 *   <li>Providers whose id appears in the configured {@code order} list, in that order.</li>
 *   <li>Any remaining providers appended alphabetically (deterministic, so logs are stable).</li>
 * </ol>
 *
 * <h4>Cooldown</h4>
 * Consecutive {@link #reportFailure(String, Throwable)} calls grow the cooldown
 * following {@link CooldownPolicy#delayForAttempt(int)}. A successful call
 * ({@link #reportSuccess(String)}) clears the counter.
 */
public class DefaultProviderRegistry implements ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultProviderRegistry.class);

    private final Map<String, ProviderClient> providers;
    private final List<String> orderedIds;
    private final CooldownPolicy cooldownPolicy;
    private final Clock clock;
    /** Nullable — when null the registry simply returns {@link Optional#empty()}
     *  from {@link #authProfile(String)} and callers fall back to properties. */
    private final AuthProfileVault vault;
    private final ConcurrentMap<String, ProviderState> state = new ConcurrentHashMap<>();

    public DefaultProviderRegistry(final Collection<ProviderClient> providers,
                                   final List<String> preferredOrder,
                                   final CooldownPolicy cooldownPolicy,
                                   final Clock clock,
                                   final AuthProfileVault vault) {
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(preferredOrder, "preferredOrder");
        this.cooldownPolicy = Objects.requireNonNull(cooldownPolicy, "cooldownPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.vault = vault;

        final Map<String, ProviderClient> byId = new LinkedHashMap<>();
        for (final ProviderClient p : providers) {
            final String id = p.providerId();
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("ProviderClient must expose a non-blank providerId: " + p);
            }
            if (byId.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate providerId: " + id);
            }
            byId.put(id, p);
        }
        this.providers = Map.copyOf(byId);

        final List<String> ordered = new ArrayList<>();
        for (final String id : preferredOrder) {
            if (byId.containsKey(id) && !ordered.contains(id)) {
                ordered.add(id);
            }
        }
        // Tail: any remaining providers appended alphabetically for determinism.
        final TreeMap<String, ProviderClient> tail = new TreeMap<>(byId);
        ordered.forEach(tail::remove);
        ordered.addAll(tail.keySet());
        this.orderedIds = List.copyOf(ordered);

        log.info("providers.registry.ready order={} cooldownInitial={} cooldownMax={} vault={}",
            orderedIds, cooldownPolicy.initialDelay(), cooldownPolicy.maxDelay(),
            vault != null ? vault.source() : "none");
    }

    public DefaultProviderRegistry(final Collection<ProviderClient> providers,
                                   final List<String> preferredOrder,
                                   final CooldownPolicy cooldownPolicy) {
        this(providers, preferredOrder, cooldownPolicy, Clock.systemUTC(), null);
    }

    public DefaultProviderRegistry(final Collection<ProviderClient> providers,
                                   final List<String> preferredOrder,
                                   final CooldownPolicy cooldownPolicy,
                                   final Clock clock) {
        this(providers, preferredOrder, cooldownPolicy, clock, null);
    }

    @Override
    public List<String> providerIds() {
        return orderedIds;
    }

    @Override
    public Optional<ProviderClient> select() {
        final Instant now = clock.instant();
        for (final String id : orderedIds) {
            final ProviderState s = state.get(id);
            if (s == null || s.cooldownUntil == null || !s.cooldownUntil.isAfter(now)) {
                return Optional.ofNullable(providers.get(id));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ProviderClient> get(final String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    @Override
    public boolean isCoolingDown(final String providerId) {
        final ProviderState s = state.get(providerId);
        return s != null && s.cooldownUntil != null && s.cooldownUntil.isAfter(clock.instant());
    }

    @Override
    public void reportFailure(final String providerId, final Throwable error) {
        if (!providers.containsKey(providerId)) {
            return;
        }
        state.compute(providerId, (id, prev) -> {
            final int failures = prev == null ? 1 : prev.consecutiveFailures + 1;
            final Instant until = clock.instant().plus(cooldownPolicy.delayForAttempt(failures));
            log.warn("providers.registry.failure providerId={} consecutive={} cooldownUntil={} reason={}",
                id, failures, until, error != null ? error.toString() : "unknown");
            return new ProviderState(failures, until);
        });
    }

    @Override
    public void reportSuccess(final String providerId) {
        if (!providers.containsKey(providerId)) {
            return;
        }
        final ProviderState removed = state.remove(providerId);
        if (removed != null && removed.consecutiveFailures > 0) {
            log.info("providers.registry.recovered providerId={} previousFailures={}",
                providerId, removed.consecutiveFailures);
        }
    }

    @Override
    public Optional<AuthProfile> authProfile(final String providerId) {
        if (vault == null || providerId == null) {
            return Optional.empty();
        }
        return vault.findFirst(providerId);
    }

    /** Mutable snapshot — replaced atomically via {@code compute}. */
    private record ProviderState(int consecutiveFailures, Instant cooldownUntil) {
    }
}
