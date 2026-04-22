package com.openclaw.routing;

import com.openclaw.common.util.Strings;
import com.openclaw.sessions.SessionKey;

import java.util.Objects;

/**
 * Result of address resolution: given an inbound message, the routing layer produces
 * a {@link RoutingKey} that tells the auto-reply pipeline which {@link SessionKey} to hit
 * and which {@link ChannelAccount} the reply should return to.
 */
public record RoutingKey(ChannelAccount account, String conversationId) {

    public RoutingKey {
        Objects.requireNonNull(account, "account");
        if (Strings.isBlank(conversationId)) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
    }

    public SessionKey toSessionKey() {
        return new SessionKey(account.channelId(), account.accountId(), conversationId);
    }

    public static RoutingKey of(final String channelId, final String accountId, final String conversationId) {
        return new RoutingKey(new ChannelAccount(channelId, accountId), conversationId);
    }
}
