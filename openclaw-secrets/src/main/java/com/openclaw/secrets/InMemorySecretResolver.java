package com.openclaw.secrets;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory secret store. Intended for tests and dev-time overrides, not for production.
 */
@Order(200)
public class InMemorySecretResolver implements SecretResolver, Ordered {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    public InMemorySecretResolver put(final String key, final String value) {
        if (key != null && value != null) {
            store.put(key, value);
        }
        return this;
    }

    @Override
    public String source() {
        return "mem";
    }

    @Override
    public Optional<String> resolve(final String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
