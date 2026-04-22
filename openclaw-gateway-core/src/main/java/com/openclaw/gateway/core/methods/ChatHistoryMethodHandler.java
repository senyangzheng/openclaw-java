package com.openclaw.gateway.core.methods;

import com.openclaw.gateway.api.GatewayRequest;
import com.openclaw.gateway.api.Methods;
import com.openclaw.gateway.core.MethodHandler;
import com.openclaw.routing.RoutingKey;
import com.openclaw.sessions.Session;
import com.openclaw.sessions.SessionRepository;

import java.util.List;
import java.util.Map;

/**
 * {@code chat.history}: returns the message list of the session identified by
 * {@code {channelId, accountId, conversationId}}. Empty list if session doesn't exist.
 */
public class ChatHistoryMethodHandler implements MethodHandler {

    private final SessionRepository sessions;

    public ChatHistoryMethodHandler(final SessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public String method() {
        return Methods.CHAT_HISTORY;
    }

    @Override
    public Map<String, Object> handle(final GatewayRequest request) {
        final String channelId = (String) request.params().getOrDefault("channelId", "web");
        final String accountId = (String) request.params().getOrDefault("accountId", "anonymous");
        final String conversationId = (String) request.params().getOrDefault("conversationId", "default");

        final RoutingKey rk = RoutingKey.of(channelId, accountId, conversationId);
        final List<Map<String, String>> messages = sessions.find(rk.toSessionKey())
            .map(Session::messages)
            .orElse(List.of())
            .stream()
            .map(m -> Map.of("role", m.role().name().toLowerCase(), "content", m.content()))
            .toList();

        return Map.of("messages", messages, "count", messages.size());
    }
}
