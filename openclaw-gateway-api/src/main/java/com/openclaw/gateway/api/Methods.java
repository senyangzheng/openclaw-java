package com.openclaw.gateway.api;

/**
 * Canonical method-name constants for the gateway control plane. Full set lands in M4;
 * M1 only needs the few methods exercised by the smoke-test pipeline.
 */
public final class Methods {

    public static final String CHAT_SEND = "chat.send";
    public static final String CHAT_HISTORY = "chat.history";
    public static final String CHANNELS_LIST = "channels.list";
    public static final String PING = "node.ping";

    private Methods() {
    }
}
