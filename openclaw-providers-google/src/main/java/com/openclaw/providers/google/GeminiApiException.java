package com.openclaw.providers.google;

/**
 * Wraps every non-2xx / malformed response from Gemini so downstream code only has
 * to catch one exception type.
 */
public class GeminiApiException extends RuntimeException {

    private final int status;

    public GeminiApiException(final int status, final String message) {
        super(message);
        this.status = status;
    }

    public GeminiApiException(final String message, final Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    public int status() {
        return status;
    }
}
