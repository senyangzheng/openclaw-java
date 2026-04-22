package com.openclaw.routing;

import com.openclaw.common.util.Strings;

/**
 * Identifies "who" on "which channel" an inbound / outbound message belongs to.
 * Corresponds to the TS {@code ChannelAccount} pair used by the original openclaw routing layer.
 */
public record ChannelAccount(String channelId, String accountId) {

    public ChannelAccount {
        if (Strings.isBlank(channelId)) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        if (Strings.isBlank(accountId)) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
    }

    public static ChannelAccount anonymousWeb() {
        return new ChannelAccount("web", "anonymous");
    }
}
