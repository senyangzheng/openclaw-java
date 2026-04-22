package com.openclaw.gateway.api;

import com.openclaw.common.util.Strings;

import java.util.Map;
import java.util.Objects;

/**
 * Envelope for gateway control-plane calls (both WS and HTTP transports eventually).
 * Shape intentionally mirrors the TS openclaw envelope so clients are wire-compatible.
 *
 * <pre>
 *   {
 *     "id": "uuid",
 *     "method": "chat.send",
 *     "authToken": "...",
 *     "params": { ... }
 *   }
 * </pre>
 */
public record GatewayRequest(
    String id,
    String method,
    String authToken,
    Map<String, Object> params
) {

    public GatewayRequest {
        if (Strings.isBlank(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (Strings.isBlank(method)) {
            throw new IllegalArgumentException("method must not be blank");
        }
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public Object param(final String name) {
        return Objects.requireNonNull(params.get(name),
            () -> "missing required param: " + name);
    }
}
