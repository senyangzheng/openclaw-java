package com.openclaw.channels.core;

import com.openclaw.routing.ChannelAccount;

/**
 * Channel-side callbacks for per-account lifecycle events.
 * In M1 only logging / bookkeeping; real handlers (session warm-up, rate-limit reset, …)
 * plug in during M3–M4.
 */
public interface AccountLifecycle {

    AccountLifecycle NOOP = new AccountLifecycle() { };

    default void onConnect(ChannelAccount account) {
    }

    default void onDisconnect(ChannelAccount account) {
    }
}
