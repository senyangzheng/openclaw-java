package com.openclaw.channels.web;

import com.openclaw.gateway.api.GatewayRequest;
import com.openclaw.gateway.api.GatewayResponse;
import com.openclaw.gateway.core.MethodDispatcher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal HTTP transport for the gateway envelope. Equivalent to the JSON-RPC entry point
 * of the TS openclaw server. M1 only accepts a single envelope per request; batch / WS
 * streaming arrive in M4.
 */
@RestController
public class GatewayHttpController {

    private final MethodDispatcher dispatcher;

    public GatewayHttpController(final MethodDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/api/gateway")
    public ResponseEntity<GatewayResponse> dispatch(@RequestBody final GatewayRequest request) {
        return ResponseEntity.ok(dispatcher.dispatch(request));
    }
}
