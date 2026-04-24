package com.openclaw.agents.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentRunStateTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(AgentRunState.IDLE.canTransitionTo(AgentRunState.QUEUED_SESSION)).isTrue();
        assertThat(AgentRunState.QUEUED_SESSION.canTransitionTo(AgentRunState.QUEUED_GLOBAL)).isTrue();
        assertThat(AgentRunState.QUEUED_GLOBAL.canTransitionTo(AgentRunState.ATTEMPTING)).isTrue();
        assertThat(AgentRunState.ATTEMPTING.canTransitionTo(AgentRunState.STREAMING)).isTrue();
        assertThat(AgentRunState.STREAMING.canTransitionTo(AgentRunState.COMPLETED)).isTrue();
        assertThat(AgentRunState.COMPLETED.canTransitionTo(AgentRunState.IDLE)).isTrue();
    }

    @Test
    void skippingStatesIsForbidden() {
        assertThat(AgentRunState.IDLE.canTransitionTo(AgentRunState.ATTEMPTING)).isFalse();
        assertThat(AgentRunState.QUEUED_SESSION.canTransitionTo(AgentRunState.STREAMING)).isFalse();
    }

    @Test
    void terminalStatesCannotRestartExceptToIdle() {
        assertThat(AgentRunState.COMPLETED.canTransitionTo(AgentRunState.QUEUED_SESSION)).isFalse();
        assertThat(AgentRunState.FAILED.canTransitionTo(AgentRunState.ATTEMPTING)).isFalse();
        assertThat(AgentRunState.FAILED.canTransitionTo(AgentRunState.IDLE)).isTrue();
    }

    @Test
    void abortingCanBeReachedFromAnyActiveState() {
        for (final AgentRunState s : new AgentRunState[]{
                AgentRunState.QUEUED_SESSION, AgentRunState.QUEUED_GLOBAL,
                AgentRunState.ATTEMPTING, AgentRunState.STREAMING, AgentRunState.COMPACTING,
                AgentRunState.WAITING_COMPACTION_RETRY}) {
            assertThat(s.canTransitionTo(AgentRunState.ABORTING))
                    .as("state %s → ABORTING", s).isTrue();
        }
    }

    @Test
    void isTerminalAndIsActiveAreExclusiveForEndStates() {
        assertThat(AgentRunState.COMPLETED.isTerminal()).isTrue();
        assertThat(AgentRunState.FAILED.isTerminal()).isTrue();
        assertThat(AgentRunState.ATTEMPTING.isActive()).isTrue();
        assertThat(AgentRunState.STREAMING.isActive()).isTrue();
        assertThat(AgentRunState.IDLE.isTerminal()).isFalse();
        assertThat(AgentRunState.IDLE.isActive()).isFalse();
    }
}
