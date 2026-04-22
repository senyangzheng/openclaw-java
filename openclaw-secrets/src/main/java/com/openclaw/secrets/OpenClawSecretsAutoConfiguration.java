package com.openclaw.secrets;

import com.openclaw.secrets.vault.AuthProfileVault;
import com.openclaw.secrets.vault.InMemoryAuthProfileVault;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Base auto-configuration for {@code openclaw-secrets}. Always registers env and
 * in-memory {@link SecretResolver}s, composing them into a {@link CompositeSecretResolver}.
 *
 * <p>Also registers a default {@link InMemoryAuthProfileVault} — useful for tests
 * and dev-time overrides. When {@code openclaw.secrets.store=jdbc} is set and
 * MyBatis-Plus is on the classpath, {@link OpenClawSecretsJdbcAutoConfiguration}
 * contributes a {@link com.openclaw.secrets.vault.jdbc.JdbcAuthProfileVault} instead;
 * consumers use {@link ConditionalOnMissingBean} to pick the JDBC one when present.
 */
@AutoConfiguration
public class OpenClawSecretsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EnvSecretResolver envSecretResolver() {
        return new EnvSecretResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public InMemorySecretResolver inMemorySecretResolver() {
        return new InMemorySecretResolver();
    }

    @Bean
    @ConditionalOnMissingBean(CompositeSecretResolver.class)
    public CompositeSecretResolver compositeSecretResolver(final List<SecretResolver> resolvers) {
        return new CompositeSecretResolver(resolvers.stream()
            .filter(r -> !(r instanceof CompositeSecretResolver))
            .toList());
    }

    /**
     * Default vault for tests / dev. Replaced by
     * {@link OpenClawSecretsJdbcAutoConfiguration#jdbcAuthProfileVault} when
     * {@code openclaw.secrets.store=jdbc}.
     */
    @Bean
    @ConditionalOnMissingBean(AuthProfileVault.class)
    public AuthProfileVault inMemoryAuthProfileVault() {
        return new InMemoryAuthProfileVault();
    }
}
