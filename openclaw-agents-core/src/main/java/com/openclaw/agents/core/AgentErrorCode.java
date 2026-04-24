package com.openclaw.agents.core;

import com.openclaw.common.error.ErrorCode;

/**
 * Error codes owned by {@code openclaw-agents-core}.
 */
public enum AgentErrorCode implements ErrorCode {

    INVALID_TRANSITION("AGENTS_4000", "Invalid agent run state transition"),
    RUN_ABORTED("AGENTS_4090", "Agent run aborted"),
    ACTIVE_RUN_CONFLICT("AGENTS_4091", "Session already has an active run");

    private final String code;
    private final String defaultMessage;

    AgentErrorCode(final String code, final String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
