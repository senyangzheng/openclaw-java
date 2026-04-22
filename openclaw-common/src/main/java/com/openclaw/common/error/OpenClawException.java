package com.openclaw.common.error;

import java.util.Map;
import java.util.Objects;

/**
 * Root runtime exception for openclaw.
 * <p>
 * Every business exception should extend this class (or be wrapped as this) so that
 * global error handlers can uniformly serialize {@link #getErrorCode()} and {@link #getContext()}.
 *
 * <p><b>Do not</b> throw raw {@link RuntimeException}; see
 * {@code .cursor/plan/05-translation-conventions.md} for rationale.
 */
public class OpenClawException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final transient Map<String, Object> context;

    public OpenClawException(final ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null, Map.of());
    }

    public OpenClawException(final ErrorCode errorCode, final String message) {
        this(errorCode, message, null, Map.of());
    }

    public OpenClawException(final ErrorCode errorCode, final String message, final Throwable cause) {
        this(errorCode, message, cause, Map.of());
    }

    public OpenClawException(final ErrorCode errorCode,
                             final String message,
                             final Throwable cause,
                             final Map<String, Object> context) {
        super(Objects.requireNonNullElse(message, errorCode.defaultMessage()), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.context = context == null ? Map.of() : Map.copyOf(context);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Structured diagnostic context (non-PII). Will be serialized to clients / logs.
     */
    public Map<String, Object> getContext() {
        return context;
    }
}
