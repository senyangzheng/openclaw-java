package com.openclaw.providers.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration surface for the provider registry.
 *
 * <pre>
 * openclaw:
 *   providers:
 *     registry:
 *       order: [google, qwen, mock]       # fallback order; missing ids are skipped
 *       cooldown:
 *         initial-delay: 5s
 *         max-delay: 5m
 *         multiplier: 2.0
 * </pre>
 *
 * <p>Unlisted providers are appended in deterministic (alphabetical) order after
 * the explicit list, so a newly-enabled provider always has a place in the chain
 * without requiring a config change.
 */
@ConfigurationProperties(prefix = "openclaw.providers.registry")
public class ProviderRegistryProperties {

    /** Preferred provider order, by {@code providerId}. First one wins when healthy. */
    private List<String> order = new ArrayList<>();

    /** If true, the registry's composite client is registered as {@code @Primary}. */
    private boolean primary = true;

    private Cooldown cooldown = new Cooldown();

    public List<String> getOrder() {
        return order;
    }

    public void setOrder(final List<String> order) {
        this.order = order == null ? new ArrayList<>() : order;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(final boolean primary) {
        this.primary = primary;
    }

    public Cooldown getCooldown() {
        return cooldown;
    }

    public void setCooldown(final Cooldown cooldown) {
        this.cooldown = cooldown == null ? new Cooldown() : cooldown;
    }

    public static class Cooldown {
        private Duration initialDelay = Duration.ofSeconds(5);
        private Duration maxDelay = Duration.ofMinutes(5);
        private double multiplier = 2.0;

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(final Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(final Duration maxDelay) {
            this.maxDelay = maxDelay;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(final double multiplier) {
            this.multiplier = multiplier;
        }
    }
}
