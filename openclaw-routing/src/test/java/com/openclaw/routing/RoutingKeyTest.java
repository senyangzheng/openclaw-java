package com.openclaw.routing;

import com.openclaw.sessions.SessionKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingKeyTest {

    @Test
    void shouldBuildSessionKey() {
        final RoutingKey key = RoutingKey.of("web", "anon", "c-42");

        final SessionKey sk = key.toSessionKey();

        assertThat(sk.asString()).isEqualTo("web:anon:c-42");
    }

    @Test
    void shouldRejectBlankInputs() {
        assertThatThrownBy(() -> RoutingKey.of("web", "anon", "  "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
