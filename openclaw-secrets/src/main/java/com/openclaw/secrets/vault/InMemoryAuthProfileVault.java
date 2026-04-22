package com.openclaw.secrets.vault;

import com.openclaw.providers.api.AuthProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory {@link AuthProfileVault}. Intended for unit tests and
 * ephemeral dev setups — the store is lost on restart.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}. Per-provider lists are rebuilt on
 * every read so the implementation stays trivial; no production workload ever
 * goes through here.
 */
public class InMemoryAuthProfileVault implements AuthProfileVault {

    /** key = "providerId|profileId" */
    private final Map<String, AuthProfile> store = new ConcurrentHashMap<>();

    @Override
    public String source() {
        return "mem";
    }

    @Override
    public Optional<AuthProfile> find(final String providerId, final String profileId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(profileId, "profileId");
        return Optional.ofNullable(store.get(compositeKey(providerId, profileId)));
    }

    @Override
    public List<AuthProfile> listByProvider(final String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        final List<AuthProfile> matches = new ArrayList<>();
        for (Map.Entry<String, AuthProfile> e : new LinkedHashMap<>(store).entrySet()) {
            if (e.getValue().providerId().equals(providerId)) {
                matches.add(e.getValue());
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public boolean supportsWrites() {
        return true;
    }

    @Override
    public void save(final AuthProfile profile) {
        Objects.requireNonNull(profile, "profile");
        store.put(compositeKey(profile.providerId(), profile.profileId()), profile);
    }

    @Override
    public void delete(final String providerId, final String profileId) {
        store.remove(compositeKey(providerId, profileId));
    }

    private static String compositeKey(final String providerId, final String profileId) {
        return providerId + '|' + profileId;
    }
}
