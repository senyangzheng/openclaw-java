package com.openclaw.commands;

import com.openclaw.autoreply.AutoReplyPipeline;
import com.openclaw.channels.core.ChannelRegistry;
import com.openclaw.channels.core.InboundMessage;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.common.error.CommonErrorCode;
import com.openclaw.common.error.OpenClawException;
import com.openclaw.common.util.Strings;
import com.openclaw.routing.RoutingKey;

import java.util.Objects;
import java.util.UUID;

/**
 * Business facade for the "send chat" operation. Shared between the CLI entry point
 * (see {@code openclaw-cli}) and future UI / script callers. Gateway dispatch uses its
 * own {@code ChatSendMethodHandler} for wire compatibility; both paths converge on
 * {@link AutoReplyPipeline}.
 */
public class ChatCommandService {

    private final AutoReplyPipeline pipeline;
    private final ChannelRegistry channels;

    public ChatCommandService(final AutoReplyPipeline pipeline, final ChannelRegistry channels) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.channels = Objects.requireNonNull(channels, "channels");
    }

    public OutboundMessage chat(final ChatCommandRequest req) {
        Objects.requireNonNull(req, "req");
        if (Strings.isBlank(req.text())) {
            throw new OpenClawException(CommonErrorCode.ILLEGAL_ARGUMENT, "text must not be blank");
        }

        final String channelId = Strings.defaultIfBlank(req.channelId(), "web");
        final String accountId = Strings.defaultIfBlank(req.accountId(), "anonymous");
        final String conversationId = Strings.defaultIfBlank(req.conversationId(), "default");

        channels.require(channelId);

        final InboundMessage inbound = new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of(channelId, accountId, conversationId),
            req.text(),
            null,
            null
        );
        return pipeline.handle(inbound);
    }

    public record ChatCommandRequest(String text, String channelId, String accountId, String conversationId) {
    }
}
