package com.openclaw.secrets.vault.jdbc;

import com.openclaw.providers.api.AuthProfile;
import com.openclaw.secrets.vault.AuthProfileVault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link JdbcAuthProfileVault} against a real MySQL 8
 * container. Covers the full envelope-encryption round-trip:
 * {@code plaintext → EnvelopeCipher.encrypt → insert → select → decrypt}.
 *
 * <p>Requires Docker (Testcontainers). Skipped on machines without Docker
 * via the standard {@code -DskipITs=true} flag.
 */
@SpringBootTest(classes = JdbcAuthProfileVaultIT.TestApp.class)
@Testcontainers
class JdbcAuthProfileVaultIT {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("openclaw")
        .withUsername("openclaw")
        .withPassword("openclaw");

    /** 32 zero bytes — deterministic for tests, never for prod. */
    private static final String KEK_BASE64 = Base64.getEncoder().encodeToString(new byte[32]);

    @DynamicPropertySource
    static void datasource(final DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", MYSQL::getJdbcUrl);
        reg.add("spring.datasource.username", MYSQL::getUsername);
        reg.add("spring.datasource.password", MYSQL::getPassword);
        reg.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        reg.add("spring.flyway.enabled", () -> "true");
        reg.add("spring.flyway.locations", () -> "classpath:db/migration");
        reg.add("openclaw.secrets.store", () -> "jdbc");
        reg.add("openclaw.secrets.crypto.kek-base64", () -> KEK_BASE64);
    }

    @Autowired
    private AuthProfileVault vault;

    @Test
    void shouldRoundTripEncryptedCredential() {
        final AuthProfile profile = new AuthProfile("default", "qwen", "sk-round-trip",
            Map.of("region", "cn-shanghai"));

        vault.save(profile);

        final Optional<AuthProfile> found = vault.find("qwen", "default");
        assertThat(found).isPresent();
        assertThat(found.get().apiKey()).isEqualTo("sk-round-trip");
        assertThat(found.get().extras()).containsEntry("region", "cn-shanghai");
    }

    @Test
    void shouldListProfilesInCreationOrder() {
        vault.save(AuthProfile.of("a", "google", "sk-a"));
        vault.save(AuthProfile.of("b", "google", "sk-b"));
        vault.save(AuthProfile.of("c", "google", "sk-c"));

        assertThat(vault.listByProvider("google"))
            .extracting(AuthProfile::profileId)
            .containsExactly("a", "b", "c");
    }

    @Test
    void saveShouldBeIdempotentForTheSameProfileId() {
        vault.save(AuthProfile.of("only", "openai", "sk-v1"));
        vault.save(new AuthProfile("only", "openai", "sk-v2", Map.of("tier", "premium")));

        final AuthProfile resolved = vault.find("openai", "only").orElseThrow();
        assertThat(resolved.apiKey()).isEqualTo("sk-v2");
        assertThat(resolved.extras()).containsEntry("tier", "premium");
        assertThat(vault.listByProvider("openai")).hasSize(1);
    }

    @Test
    void deleteRemovesTheRow() {
        vault.save(AuthProfile.of("ephemeral", "qwen", "sk-ephemeral"));
        vault.delete("qwen", "ephemeral");
        assertThat(vault.find("qwen", "ephemeral")).isEmpty();
    }

    @Test
    void findFirstReturnsFirstRowForProvider() {
        vault.save(AuthProfile.of("primary", "mistral", "sk-primary"));
        vault.save(AuthProfile.of("secondary", "mistral", "sk-secondary"));

        final AuthProfile first = vault.findFirst("mistral").orElseThrow();
        assertThat(first.profileId()).isEqualTo("primary");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }
}
