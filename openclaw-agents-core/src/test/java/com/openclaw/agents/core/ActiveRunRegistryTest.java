package com.openclaw.agents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.sessions.SessionKey;

import org.junit.jupiter.api.Test;

class ActiveRunRegistryTest {

    private final SessionKey key = new SessionKey("web", "u1", "c1");

    @Test
    void tryRegisterReturnsFalseOnConflict() {
        final ActiveRunRegistry registry = new ActiveRunRegistry();
        final AgentRunHandle a = AgentRunHandle.create(key);
        final AgentRunHandle b = AgentRunHandle.create(key);
        assertThat(registry.tryRegister(a)).isTrue();
        assertThat(registry.tryRegister(b)).isFalse();
        assertThat(registry.current(key)).hasValue(a);
    }

    @Test
    void registerOrThrowFailsOnConflict() {
        final ActiveRunRegistry registry = new ActiveRunRegistry();
        final AgentRunHandle a = AgentRunHandle.create(key);
        registry.registerOrThrow(a);
        assertThatThrownBy(() -> registry.registerOrThrow(AgentRunHandle.create(key)))
                .isInstanceOf(OpenClawException.class);
    }

    @Test
    void clearIfMatchesOnlyClearsForMatchingHandle() {
        final ActiveRunRegistry registry = new ActiveRunRegistry();
        final AgentRunHandle a = AgentRunHandle.create(key);
        final AgentRunHandle b = AgentRunHandle.create(key);
        registry.tryRegister(a);
        assertThat(registry.clearIfMatches(key, b)).isFalse();
        assertThat(registry.current(key)).hasValue(a);
        assertThat(registry.clearIfMatches(key, a)).isTrue();
        assertThat(registry.current(key)).isEmpty();
    }

    @Test
    void stalePrevHandleDoesNotClearSuccessor() {
        final ActiveRunRegistry registry = new ActiveRunRegistry();
        final AgentRunHandle prev = AgentRunHandle.create(key);
        final AgentRunHandle succ = AgentRunHandle.create(key);
        registry.tryRegister(prev);
        registry.clearIfMatches(key, prev);
        registry.tryRegister(succ);
        // prev cleanup firing late (simulating race): must be a no-op
        assertThat(registry.clearIfMatches(key, prev)).isFalse();
        assertThat(registry.current(key)).hasValue(succ);
    }
}
