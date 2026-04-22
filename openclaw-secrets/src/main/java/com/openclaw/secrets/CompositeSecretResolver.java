package com.openclaw.secrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Composes multiple {@link SecretResolver}s and returns the first non-empty value.
 * Resolvers are consulted in {@code @Order} ascending order.
 */
public class CompositeSecretResolver implements SecretResolver {

    private static final Logger log = LoggerFactory.getLogger(CompositeSecretResolver.class);

    private final List<SecretResolver> resolvers;

    public CompositeSecretResolver(final List<SecretResolver> resolvers) {
        Objects.requireNonNull(resolvers, "resolvers");
        final List<SecretResolver> copy = new ArrayList<>(resolvers);
        copy.sort(Comparator.comparingInt(r -> r instanceof org.springframework.core.Ordered o ? o.getOrder() : 0));
        this.resolvers = List.copyOf(copy);
    }

    @Override
    public String source() {
        return "composite";
    }

    @Override
    public Optional<String> resolve(final String key) {
        for (SecretResolver resolver : resolvers) {
            final Optional<String> value = resolver.resolve(key);
            if (value.isPresent()) {
                log.debug("secret.resolved key={} source={}", key, resolver.source());
                return value;
            }
        }
        log.debug("secret.miss key={}", key);
        return Optional.empty();
    }

    List<SecretResolver> resolvers() {
        return resolvers;
    }
}
