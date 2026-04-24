package com.openclaw.hooks;

import com.openclaw.common.error.ErrorCode;

/**
 * Error codes owned by {@code openclaw-hooks-runtime}.
 */
public enum HookErrorCode implements ErrorCode {

    HOOK_BLOCKED("HOOKS_4030", "Hook blocked the action"),
    HOOK_REGISTRATION_CONFLICT("HOOKS_4090", "Hook registration conflict");

    private final String code;
    private final String defaultMessage;

    HookErrorCode(final String code, final String defaultMessage) {
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
