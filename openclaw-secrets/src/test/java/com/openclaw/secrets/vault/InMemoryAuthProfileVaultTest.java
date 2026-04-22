package com.openclaw.secrets.vault;

import com.openclaw.providers.api.AuthProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuthProfileVaultTest {

    @Test
    void shouldUpsertFindAndList() {
        final InMemoryAuthProfileVault vault = new InMemoryAuthProfileVault();
        final AuthProfile first = new AuthProfile("default", "qwen", "sk-a", Map.of("region", "cn-shanghai"));
        final AuthProfile second = new AuthProfile("fallback", "qwen", "sk-b", Map.of());

        vault.save(first);
        vault.save(second);

        assertThat(vault.find("qwen", "default")).contains(first);
        assertThat(vault.find("qwen", "fallback")).contains(second);
        assertThat(vault.listByProvider("qwen")).containsExactlyInAnyOrder(first, second);
        assertThat(vault.findFirst("qwen")).isPresent();
    }

    @Test
    void findFirstReturnsEmptyWhenProviderUnknown() {
        final InMemoryAuthProfileVault vault = new InMemoryAuthProfileVault();
        vault.save(AuthProfile.of("only", "qwen", "sk"));
        assertThat(vault.findFirst("gemini")).isEmpty();
    }

    @Test
    void deleteRemovesOnlyTheTargetProfile() {
        final InMemoryAuthProfileVault vault = new InMemoryAuthProfileVault();
        vault.save(AuthProfile.of("a", "qwen", "sk-a"));
        vault.save(AuthProfile.of("b", "qwen", "sk-b"));

        vault.delete("qwen", "a");

        assertThat(vault.find("qwen", "a")).isEmpty();
        assertThat(vault.listByProvider("qwen")).hasSize(1);
    }
}
