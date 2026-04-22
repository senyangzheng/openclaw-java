package com.openclaw.common.error;

/**
 * Framework-level error codes owned by {@code openclaw-common}.
 * Business modules MUST define their own enum and not reuse these.
 */
public enum CommonErrorCode implements ErrorCode {

    INTERNAL_ERROR("COMMON_5000", "Unexpected internal error"),
    ILLEGAL_ARGUMENT("COMMON_4000", "Illegal argument"),
    ILLEGAL_STATE("COMMON_4001", "Illegal state"),
    NOT_FOUND("COMMON_4040", "Resource not found"),
    UNSUPPORTED("COMMON_4010", "Operation not supported in current scope"),
    JSON_SERIALIZE("COMMON_5001", "Json serialization failed"),
    JSON_DESERIALIZE("COMMON_5002", "Json deserialization failed");

    private final String code;
    private final String defaultMessage;

    CommonErrorCode(final String code, final String defaultMessage) {
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
