package com.openclaw.providers.registry;

import com.openclaw.providers.api.AuthProfile;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.CooldownPolicy;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.secrets.vault.InMemoryAuthProfileVault;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultProviderRegistryTest {

    private static ProviderClient stub(final String id) {
        return new ProviderClient() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public ChatResponse chat(final ChatRequest request) {
                return new ChatResponse(id, "m", id + "-response",
                    ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY, Duration.ZERO);
            }

            @Override
            public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
                return Flux.just(
                    (ChatChunkEvent) new ChatChunkEvent.Delta(id + "-delta"),
                    new ChatChunkEvent.Done(ChatResponse.FinishReason.STOP, ChatResponse.Usage.EMPTY)
                );
            }
        };
    }

    @Test
    void shouldRespectPreferredOrderAndAppendUnlistedProvidersAlphabetically() {
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(stub("mock"), stub("qwen"), stub("google"), stub("anthropic")),
            List.of("google", "qwen"),
            CooldownPolicy.DEFAULT);

        assertThat(registry.providerIds()).containsExactly("google", "qwen", "anthropic", "mock");
        assertThat(registry.select()).map(ProviderClient::providerId).hasValue("google");
    }

    @Test
    void shouldRejectDuplicateProviderIds() {
        assertThatThrownBy(() -> new DefaultProviderRegistry(
            List.of(stub("qwen"), stub("qwen")),
            List.of(),
            CooldownPolicy.DEFAULT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate");
    }

    @Test
    void shouldPutFailedProviderOnCooldownAndFallBack() {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        final Clock frozen = Clock.fixed(now.get(), ZoneOffset.UTC);

        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(stub("google"), stub("qwen")),
            List.of("google", "qwen"),
            new CooldownPolicy(Duration.ofSeconds(5), Duration.ofMinutes(1), 2.0),
            frozen);

        assertThat(registry.isCoolingDown("google")).isFalse();

        registry.reportFailure("google", new RuntimeException("boom"));
        assertThat(registry.isCoolingDown("google")).isTrue();
        assertThat(registry.select()).map(ProviderClient::providerId).hasValue("qwen");

        registry.reportFailure("google", new RuntimeException("boom2"));
        // still cooling down; only qwen is healthy
        assertThat(registry.select()).map(ProviderClient::providerId).hasValue("qwen");
    }

    @Test
    void shouldClearCooldownOnReportSuccess() {
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(stub("google"), stub("qwen")),
            List.of("google", "qwen"),
            CooldownPolicy.DEFAULT);

        registry.reportFailure("google", new RuntimeException("x"));
        assertThat(registry.isCoolingDown("google")).isTrue();

        registry.reportSuccess("google");
        assertThat(registry.isCoolingDown("google")).isFalse();
        assertThat(registry.select()).map(ProviderClient::providerId).hasValue("google");
    }

    @Test
    void shouldReturnEmptyWhenAllProvidersCoolingDown() {
        final Clock frozen = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(stub("a"), stub("b")),
            List.of("a", "b"),
            new CooldownPolicy(Duration.ofMinutes(10), Duration.ofHours(1), 1.0),
            frozen);

        registry.reportFailure("a", new RuntimeException());
        registry.reportFailure("b", new RuntimeException());

        assertThat(registry.select()).isEmpty();
    }

    @Test
    void shouldIgnoreReportsForUnknownProviders() {
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(stub("a")), List.of(), CooldownPolicy.DEFAULT);

        registry.reportFailure("nonexistent", new RuntimeException());
        registry.reportSuccess("nonexistent");

        assertThat(registry.isCoolingDown("nonexistent")).isFalse();
    }

    @Test
    void authProfileReturnsEmptyWhenNoVaultWired() {
        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(stub("qwen")), List.of(), CooldownPolicy.DEFAULT);

        assertThat(registry.authProfile("qwen")).isEmpty();
    }

    @Test
    void authProfileDelegatesToWiredVault() {
        final InMemoryAuthProfileVault vault = new InMemoryAuthProfileVault();
        vault.save(AuthProfile.of("default", "qwen", "sk-xyz"));

        final DefaultProviderRegistry registry = new DefaultProviderRegistry(
            List.of(stub("qwen"), stub("google")),
            List.of("qwen", "google"),
            CooldownPolicy.DEFAULT,
            Clock.systemUTC(),
            vault);

        assertThat(registry.authProfile("qwen"))
            .map(AuthProfile::apiKey).hasValue("sk-xyz");
        assertThat(registry.authProfile("google")).isEmpty();
        assertThat(registry.authProfile(null)).isEmpty();
    }
}
