package com.openclaw.gateway.core.methods;

import com.openclaw.gateway.api.GatewayRequest;
import com.openclaw.gateway.api.Methods;
import com.openclaw.gateway.core.MethodHandler;

import java.time.Instant;
import java.util.Map;

/**
 * {@code node.ping}: zero-arg liveness probe used by CLI / WS handshake smoke tests.
 */
public class PingMethodHandler implements MethodHandler {

    @Override
    public String method() {
        return Methods.PING;
    }

    @Override
    public Map<String, Object> handle(final GatewayRequest request) {
        return Map.of("pong", true, "ts", Instant.now().toString());
    }
}
