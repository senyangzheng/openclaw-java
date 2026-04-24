package com.openclaw.lanes;

import com.openclaw.common.error.OpenClawException;

/**
 * Thrown to queued tasks that get cancelled by {@code LaneDispatcher.clearLane(lane)} or
 * {@code LaneDispatcher.resetAllLanes(reason)}.
 * <p>
 * Tasks already in the {@code active} set are <b>not</b> affected; only tasks still waiting in the queue
 * are rejected with this exception.
 */
public final class CommandLaneClearedException extends OpenClawException {

    private static final long serialVersionUID = 1L;

    private final String reason;

    public CommandLaneClearedException(final String reason) {
        super(LaneErrorCode.LANE_CLEARED, "Command lane cleared: " + (reason == null ? "(unspecified)" : reason));
        this.reason = reason == null ? "" : reason;
    }

    public String reason() {
        return reason;
    }
}
