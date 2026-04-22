package com.openclaw.secrets;

import java.util.Optional;

/**
 * Resolve secrets (API keys, tokens, passwords) by a logical key.
 * <p>
 * Multiple implementations may coexist; they are ordered via {@link org.springframework.core.annotation.Order}
 * and composed by {@link CompositeSecretResolver}.
 *
 * <p>Implementations MUST:
 * <ul>
 *   <li>never log the resolved value, only the key and the source name;</li>
 *   <li>return {@link Optional#empty()} when a key is unknown, not throw.</li>
 * </ul>
 */
public interface SecretResolver {

    /** A stable short identifier, e.g. {@code "env"}, {@code "mem"}, {@code "file"}, {@code "vault"}. */
    String source();

    Optional<String> resolve(String key);
}
