package com.openclaw.channels.web;

import com.openclaw.channels.core.ChannelAdapter;
import com.openclaw.channels.core.OutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * M1 Web channel: delivery is synchronous — the HTTP handler pipes the outbound message
 * straight back to the caller as JSON response, so {@link #deliver(OutboundMessage)} is a no-op.
 * <p>
 * WebSocket push / long-poll delivery is implemented in M4 when {@code openclaw-gateway-ws} lands.
 */
public class WebChannelAdapter implements ChannelAdapter {

    public static final String CHANNEL_ID = "web";
    private static final Logger log = LoggerFactory.getLogger(WebChannelAdapter.class);

    @Override
    public String channelId() {
        return CHANNEL_ID;
    }

    @Override
    public void deliver(final OutboundMessage outbound) {
        log.debug("web.channel.deliver messageId={} session={}",
            outbound.messageId(), outbound.routingKey().toSessionKey().asString());
    }
}
