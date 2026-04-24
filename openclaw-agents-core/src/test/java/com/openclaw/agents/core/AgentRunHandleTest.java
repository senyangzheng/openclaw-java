package com.openclaw.agents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.sessions.SessionKey;

import org.junit.jupiter.api.Test;

class AgentRunHandleTest {

    @Test
    void validTransitionsAdvanceState() {
        final AgentRunHandle h = AgentRunHandle.create(new SessionKey("web", "u", "c"));
        assertThat(h.currentState()).isEqualTo(AgentRunState.IDLE);
        h.advance(AgentRunState.QUEUED_SESSION);
        h.advance(AgentRunState.QUEUED_GLOBAL);
        h.advance(AgentRunState.ATTEMPTING);
        h.advance(AgentRunState.STREAMING);
        h.advance(AgentRunState.COMPLETED);
        assertThat(h.currentState()).isEqualTo(AgentRunState.COMPLETED);
    }

    @Test
    void illegalTransitionThrows() {
        final AgentRunHandle h = AgentRunHandle.create(new SessionKey("web", "u", "c"));
        assertThatThrownBy(() -> h.advance(AgentRunState.STREAMING))
                .isInstanceOf(OpenClawException.class)
                .hasMessageContaining("Illegal transition");
    }

    @Test
    void abortIsIdempotent() {
        final AgentRunHandle h = AgentRunHandle.create(new SessionKey("web", "u", "c"));
        h.abort("user");
        h.abort("late-retry");
        assertThat(h.isAborted()).isTrue();
        assertThat(h.abortReason()).isEqualTo("user");
    }
}
