package com.openclaw.secrets;

import com.openclaw.common.util.Strings;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves a secret from OS environment variables.
 * <p>
 * Keys are normalized to {@code UPPER_SNAKE_CASE} when looking up, so caller may use either form:
 * {@code openclaw.providers.google.api-key} &rarr; {@code OPENCLAW_PROVIDERS_GOOGLE_API_KEY}.
 */
@Order(100)
public class EnvSecretResolver implements SecretResolver, Ordered {

    @Override
    public String source() {
        return "env";
    }

    @Override
    public Optional<String> resolve(final String key) {
        if (Strings.isBlank(key)) {
            return Optional.empty();
        }
        final String candidate = System.getenv(key);
        if (Strings.isNotBlank(candidate)) {
            return Optional.of(candidate);
        }
        final String normalized = key
            .replace('-', '_')
            .replace('.', '_')
            .toUpperCase(Locale.ROOT);
        return Optional.ofNullable(System.getenv(normalized))
            .filter(Strings::isNotBlank);
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
