package com.openclaw.gateway.core;

import com.openclaw.gateway.api.GatewayRequest;

import java.util.Map;

/**
 * A handler for a single gateway method. Implementations are registered as Spring beans
 * and discovered by {@link MethodDispatcher} at startup.
 */
public interface MethodHandler {

    /** Method name this handler serves, e.g. {@code "chat.send"}. */
    String method();

    /**
     * Invoke the method. Input is the raw params map; the dispatcher has already validated
     * presence of {@link GatewayRequest#id()} and (optionally) the auth token.
     */
    Map<String, Object> handle(GatewayRequest request);
}
