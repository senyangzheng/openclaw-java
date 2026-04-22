package com.openclaw.channels.core;

import com.openclaw.common.error.CommonErrorCode;
import com.openclaw.common.error.OpenClawException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central lookup of {@link ChannelAdapter} instances by {@code channelId}.
 * Registered automatically by Spring (all {@link ChannelAdapter} beans are collected).
 */
public class ChannelRegistry {

    private final Map<String, ChannelAdapter> byId;

    public ChannelRegistry(final List<ChannelAdapter> channels) {
        Objects.requireNonNull(channels, "channels");
        this.byId = channels.stream()
            .collect(Collectors.toUnmodifiableMap(
                ChannelAdapter::channelId,
                c -> c,
                (existing, dup) -> {
                    throw new OpenClawException(CommonErrorCode.ILLEGAL_STATE,
                        "Duplicate channel id: " + existing.channelId());
                }
            ));
    }

    public Optional<ChannelAdapter> find(final String channelId) {
        return Optional.ofNullable(byId.get(channelId));
    }

    public ChannelAdapter require(final String channelId) {
        return find(channelId).orElseThrow(() ->
            new OpenClawException(CommonErrorCode.NOT_FOUND, "Unknown channel: " + channelId));
    }

    public List<String> channelIds() {
        return List.copyOf(byId.keySet());
    }
}
