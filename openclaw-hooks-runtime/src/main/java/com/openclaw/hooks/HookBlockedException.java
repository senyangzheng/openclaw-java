package com.openclaw.hooks;

import com.openclaw.common.error.OpenClawException;

/**
 * Thrown by {@link HookRunner#runModifyingHook} when any hook returns a {@link HookOutcome.Block}.
 * <p>
 * Callers (tool execute / agent start) must catch this exception and treat it as "stop the action; do not
 * invoke the main branch". The blocked reason is preserved verbatim in {@link #getMessage()}.
 */
public final class HookBlockedException extends OpenClawException {

    private static final long serialVersionUID = 1L;

    private final String hookName;
    private final String handlerId;
    private final String blockReason;

    public HookBlockedException(final String hookName, final String handlerId, final String blockReason) {
        super(HookErrorCode.HOOK_BLOCKED,
                "Hook " + hookName + " (handler=" + handlerId + ") blocked: " + blockReason);
        this.hookName = hookName;
        this.handlerId = handlerId;
        this.blockReason = blockReason == null ? "" : blockReason;
    }

    public String hookName() {
        return hookName;
    }

    public String handlerId() {
        return handlerId;
    }

    public String blockReason() {
        return blockReason;
    }
}
