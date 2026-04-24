package com.openclaw.lanes;

import com.openclaw.common.error.ErrorCode;

/**
 * Error codes owned by {@code openclaw-session-lanes}.
 */
public enum LaneErrorCode implements ErrorCode {

    LANE_CLEARED("LANES_4090", "Command lane cleared"),
    LANE_QUEUE_OVERFLOW("LANES_4091", "Lane queue is full");

    private final String code;
    private final String defaultMessage;

    LaneErrorCode(final String code, final String defaultMessage) {
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
