package com.openclaw.providers.qwen;

/**
 * Thrown by {@link QwenProviderClient} whenever a call to the DashScope
 * OpenAI-compatible endpoint fails — non-2xx HTTP status, malformed JSON body,
 * or transport-level I/O issues.
 *
 * <p>Kept as a {@link RuntimeException} so pipelines aren't forced to declare
 * it everywhere; the auto-reply loop (M2+) will translate it into a graceful
 * user-visible reply or a retryable signal.
 */
public class QwenApiException extends RuntimeException {

    private final Integer httpStatus;

    public QwenApiException(final String message, final Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
    }

    public QwenApiException(final int httpStatus, final String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /** HTTP status if the failure came from an upstream response, otherwise {@code null}. */
    public Integer httpStatus() {
        return httpStatus;
    }
}
