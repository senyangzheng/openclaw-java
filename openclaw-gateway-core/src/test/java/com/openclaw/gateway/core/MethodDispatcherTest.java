package com.openclaw.gateway.core;

import com.openclaw.agents.core.ActiveRunRegistry;
import com.openclaw.agents.core.AttemptExecutor;
import com.openclaw.agents.core.PiAgentRunner;
import com.openclaw.autoreply.AutoReplyPipeline;
import com.openclaw.gateway.api.GatewayRequest;
import com.openclaw.gateway.api.GatewayResponse;
import com.openclaw.gateway.api.Methods;
import com.openclaw.gateway.core.methods.ChatSendMethodHandler;
import com.openclaw.gateway.core.methods.PingMethodHandler;
import com.openclaw.hooks.HookRunner;
import com.openclaw.lanes.SessionLaneCoordinator;
import com.openclaw.providers.api.mock.EchoMockProviderClient;
import com.openclaw.providers.registry.ProviderDispatcher;
import com.openclaw.sessions.InMemorySessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MethodDispatcherTest {

    @Test
    void shouldDispatchPing() {
        final MethodDispatcher dispatcher = new MethodDispatcher(
            List.of(new PingMethodHandler()), new MockAuthGuard(null)
        );

        final GatewayResponse resp = dispatcher.dispatch(
            new GatewayRequest("r-1", Methods.PING, null, Map.of())
        );

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.result()).containsEntry("pong", true);
    }

    @Test
    void shouldReturnNotFoundForUnknownMethod() {
        final MethodDispatcher dispatcher = new MethodDispatcher(
            List.of(new PingMethodHandler()), new MockAuthGuard(null)
        );

        final GatewayResponse resp = dispatcher.dispatch(
            new GatewayRequest("r-2", "bogus.method", null, Map.of())
        );

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo("COMMON_4040");
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenMismatches() {
        final MethodDispatcher dispatcher = new MethodDispatcher(
            List.of(new PingMethodHandler()), new MockAuthGuard("secret")
        );

        final GatewayResponse resp = dispatcher.dispatch(
            new GatewayRequest("r-3", Methods.PING, "wrong", Map.of())
        );

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo("GATEWAY_4010");
    }

    @Test
    void shouldDispatchChatSendThroughPipeline() {
        final SessionLaneCoordinator lanes = new SessionLaneCoordinator();
        try {
            final AttemptExecutor attempt = new AttemptExecutor(
                ProviderDispatcher.direct(new EchoMockProviderClient()), new HookRunner());
            final PiAgentRunner runner = new PiAgentRunner(lanes, new ActiveRunRegistry(), attempt);
            final AutoReplyPipeline pipeline = new AutoReplyPipeline(new InMemorySessionRepository(), runner);
            final MethodDispatcher dispatcher = new MethodDispatcher(
                List.of(new ChatSendMethodHandler(pipeline)), new MockAuthGuard(null)
            );

            final GatewayResponse resp = dispatcher.dispatch(
                new GatewayRequest("r-4", Methods.CHAT_SEND, null, Map.of("text", "hi"))
            );

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.result()).containsEntry("text", "[mock] hi");
        } finally {
            lanes.close();
        }
    }
}
