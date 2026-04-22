package com.openclaw.gateway.api;

import com.openclaw.common.error.ErrorCode;

import java.util.Map;
import java.util.Objects;

/**
 * Envelope for gateway responses. Either {@link #result} is non-null (success) or {@link #error} is (failure),
 * never both. Streaming frames are modelled separately in M2.
 */
public record GatewayResponse(
    String id,
    Map<String, Object> result,
    GatewayError error
) {

    public GatewayResponse {
        Objects.requireNonNull(id, "id");
        result = result == null ? null : Map.copyOf(result);
    }

    public static GatewayResponse success(final String id, final Map<String, Object> result) {
        return new GatewayResponse(id, result, null);
    }

    public static GatewayResponse failure(final String id, final ErrorCode code, final String message) {
        return new GatewayResponse(id, null, new GatewayError(code.code(), message));
    }

    public boolean isSuccess() {
        return error == null;
    }

    public record GatewayError(String code, String message) {
    }
}
