package com.openclaw.providers.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CooldownPolicyTest {

    @Test
    void shouldProduceExponentialBackoffAndCapAtMaxDelay() {
        final CooldownPolicy policy = new CooldownPolicy(
            Duration.ofSeconds(1), Duration.ofSeconds(10), 2.0);

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.delayForAttempt(5)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayForAttempt(20)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldReturnZeroForNonPositiveAttempt() {
        assertThat(CooldownPolicy.DEFAULT.delayForAttempt(0)).isEqualTo(Duration.ZERO);
        assertThat(CooldownPolicy.DEFAULT.delayForAttempt(-5)).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldRejectInvalidConstructorArgs() {
        assertThatThrownBy(() -> new CooldownPolicy(Duration.ZERO, Duration.ofSeconds(1), 2.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CooldownPolicy(Duration.ofSeconds(2), Duration.ofSeconds(1), 2.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CooldownPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), 0.5))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
