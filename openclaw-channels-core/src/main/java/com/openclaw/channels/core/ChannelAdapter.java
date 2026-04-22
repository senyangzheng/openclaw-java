package com.openclaw.channels.core;

/**
 * Core channel SPI. A channel is responsible for:
 * <ul>
 *   <li>ingesting events from its transport (HTTP webhook, WS frame, …) and
 *       converting them into {@link InboundMessage};</li>
 *   <li>delivering {@link OutboundMessage} back through the same transport.</li>
 * </ul>
 *
 * <p>Concrete channels for M1: only {@code openclaw-channels-web}. Other transports
 * (Telegram, Slack, …) are deferred per {@code .cursor/plan/01-tech-stack.md} §6.
 */
public interface ChannelAdapter {

    /** Stable, short channel identifier (e.g. {@code "web"}). */
    String channelId();

    /** Deliver a reply back to the originating account. */
    void deliver(OutboundMessage outbound);

    /**
     * Lifecycle callbacks. A channel <b>may</b> override to react to connect / disconnect events;
     * the default does nothing so most implementations don't need it.
     */
    default AccountLifecycle lifecycle() {
        return AccountLifecycle.NOOP;
    }
}
