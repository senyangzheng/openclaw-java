package com.openclaw.gateway.core.methods;

import com.openclaw.autoreply.AutoReplyPipeline;
import com.openclaw.channels.core.InboundMessage;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.common.error.CommonErrorCode;
import com.openclaw.common.error.OpenClawException;
import com.openclaw.gateway.api.GatewayRequest;
import com.openclaw.gateway.api.Methods;
import com.openclaw.gateway.core.MethodHandler;
import com.openclaw.routing.RoutingKey;

import java.util.Map;
import java.util.UUID;

/**
 * {@code chat.send}: params = {@code {channelId, accountId, conversationId, text}}.
 * Returns the {@link OutboundMessage} produced by {@link AutoReplyPipeline}.
 */
public class ChatSendMethodHandler implements MethodHandler {

    private final AutoReplyPipeline pipeline;

    public ChatSendMethodHandler(final AutoReplyPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public String method() {
        return Methods.CHAT_SEND;
    }

    @Override
    public Map<String, Object> handle(final GatewayRequest request) {
        final String text = requireString(request.params(), "text");
        final String channelId = optionalString(request.params(), "channelId", "web");
        final String accountId = optionalString(request.params(), "accountId", "anonymous");
        final String conversationId = optionalString(request.params(), "conversationId", "default");

        final InboundMessage inbound = new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of(channelId, accountId, conversationId),
            text,
            null,
            null
        );

        final OutboundMessage reply = pipeline.handle(inbound);

        return Map.of(
            "messageId", reply.messageId(),
            "replyToMessageId", reply.replyToMessageId(),
            "text", reply.text(),
            "channelId", channelId,
            "accountId", accountId,
            "conversationId", conversationId
        );
    }

    private static String requireString(final Map<String, Object> params, final String name) {
        final Object value = params.get(name);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new OpenClawException(CommonErrorCode.ILLEGAL_ARGUMENT,
                "Missing or blank string param: " + name);
        }
        return s;
    }

    private static String optionalString(final Map<String, Object> params, final String name, final String fallback) {
        final Object value = params.get(name);
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }
}
