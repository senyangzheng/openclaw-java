package com.openclaw.providers.registry;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * Best-effort mapper from raw {@link Throwable} to {@link FailoverReason}. Keep it framework-independent
 * so every provider (Qwen/Gemini/future local) produces the same reason vocabulary.
 *
 * <p>Classification order (first match wins):
 * <ol>
 *   <li>{@link InterruptedException} / {@link CancellationException} — {@link FailoverReason#ABORTED}</li>
 *   <li>{@link TimeoutException} / {@link SocketTimeoutException} — {@link FailoverReason#TIMEOUT}</li>
 *   <li>{@link ConnectException} / {@link UnknownHostException} / other {@link IOException} —
 *       {@link FailoverReason#NETWORK}</li>
 *   <li>Status-coded exceptions (any ex exposing {@code int statusCode()} or whose message starts with
 *       {@code "HTTP <n>"}) — mapped to {@link FailoverReason#RATE_LIMIT} / {@link FailoverReason#AUTH} /
 *       {@link FailoverReason#SERVER_ERROR} / {@link FailoverReason#CLIENT_ERROR}</li>
 *   <li>Message-keyword fallback for {@code "rate limit"}, {@code "unauthorized"}, {@code "cancel"} etc.</li>
 *   <li>Otherwise {@link FailoverReason#UNKNOWN}</li>
 * </ol>
 */
public final class FailoverReasonClassifier {

    private FailoverReasonClassifier() {
    }

    public static FailoverReason classify(final Throwable error) {
        if (error == null) {
            return FailoverReason.UNKNOWN;
        }
        final Throwable root = rootCause(error);
        if (root instanceof InterruptedException || root instanceof CancellationException) {
            return FailoverReason.ABORTED;
        }
        if (root instanceof TimeoutException || root instanceof SocketTimeoutException) {
            return FailoverReason.TIMEOUT;
        }
        if (root instanceof ConnectException || root instanceof UnknownHostException) {
            return FailoverReason.NETWORK;
        }
        final Integer status = extractStatusCode(error);
        if (status != null) {
            if (status == 408 || status == 504) {
                return FailoverReason.TIMEOUT;
            }
            if (status == 429) {
                return FailoverReason.RATE_LIMIT;
            }
            if (status == 401 || status == 403) {
                return FailoverReason.AUTH;
            }
            if (status >= 500) {
                return FailoverReason.SERVER_ERROR;
            }
            if (status >= 400) {
                return FailoverReason.CLIENT_ERROR;
            }
        }
        if (root instanceof IOException) {
            return FailoverReason.NETWORK;
        }
        final String msg = lowerMessage(error);
        if (msg.contains("rate limit") || msg.contains("quota")) {
            return FailoverReason.RATE_LIMIT;
        }
        if (msg.contains("unauthorized") || msg.contains("forbidden") || msg.contains("invalid api key")) {
            return FailoverReason.AUTH;
        }
        if (msg.contains("cancel") || msg.contains("abort")) {
            return FailoverReason.ABORTED;
        }
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return FailoverReason.TIMEOUT;
        }
        return FailoverReason.UNKNOWN;
    }

    private static Throwable rootCause(final Throwable t) {
        Throwable cur = t;
        int guard = 0;
        while (cur.getCause() != null && cur.getCause() != cur && guard++ < 16) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String lowerMessage(final Throwable t) {
        final StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int guard = 0;
        while (cur != null && guard++ < 16) {
            final String m = cur.getMessage();
            if (m != null) {
                sb.append(m).append(' ');
            }
            cur = cur.getCause();
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static Integer extractStatusCode(final Throwable error) {
        Throwable cur = error;
        int guard = 0;
        while (cur != null && guard++ < 16) {
            try {
                final var method = cur.getClass().getMethod("statusCode");
                if (method.getReturnType() == int.class || method.getReturnType() == Integer.class) {
                    final Object val = method.invoke(cur);
                    if (val instanceof Integer i) {
                        return i;
                    }
                }
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // fall through to message parsing
            }
            final String msg = cur.getMessage();
            if (msg != null && msg.startsWith("HTTP ")) {
                final int idx = msg.indexOf(' ', 5);
                final String code = msg.substring(5, idx > 0 ? idx : Math.min(8, msg.length()));
                try {
                    return Integer.parseInt(code.trim());
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
            cur = cur.getCause();
        }
        return null;
    }
}
