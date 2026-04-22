package com.openclaw.gateway.core.methods;

import com.openclaw.channels.core.ChannelRegistry;
import com.openclaw.gateway.api.GatewayRequest;
import com.openclaw.gateway.api.Methods;
import com.openclaw.gateway.core.MethodHandler;

import java.util.Map;

/**
 * {@code channels.list}: returns the ids of all channels registered at runtime.
 */
public class ChannelsListMethodHandler implements MethodHandler {

    private final ChannelRegistry registry;

    public ChannelsListMethodHandler(final ChannelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String method() {
        return Methods.CHANNELS_LIST;
    }

    @Override
    public Map<String, Object> handle(final GatewayRequest request) {
        return Map.of("channels", registry.channelIds());
    }
}
