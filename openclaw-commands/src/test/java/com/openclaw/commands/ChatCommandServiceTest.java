package com.openclaw.commands;

import com.openclaw.agents.core.ActiveRunRegistry;
import com.openclaw.agents.core.AttemptExecutor;
import com.openclaw.agents.core.PiAgentRunner;
import com.openclaw.autoreply.AutoReplyPipeline;
import com.openclaw.channels.core.ChannelAdapter;
import com.openclaw.channels.core.ChannelRegistry;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.common.error.OpenClawException;
import com.openclaw.hooks.HookRunner;
import com.openclaw.lanes.SessionLaneCoordinator;
import com.openclaw.providers.api.mock.EchoMockProviderClient;
import com.openclaw.providers.registry.ProviderDispatcher;
import com.openclaw.sessions.InMemorySessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatCommandServiceTest {

    private final SessionLaneCoordinator lanes = new SessionLaneCoordinator();

    @AfterEach
    void tearDown() {
        lanes.close();
    }

    @Test
    void shouldRouteChatThroughPipeline() {
        final ChatCommandService svc = newService();

        final OutboundMessage reply = svc.chat(
            new ChatCommandService.ChatCommandRequest("hi", null, null, null)
        );

        assertThat(reply.text()).isEqualTo("[mock] hi");
    }

    @Test
    void shouldRejectUnknownChannel() {
        final ChatCommandService svc = newService();

        assertThatThrownBy(() -> svc.chat(
            new ChatCommandService.ChatCommandRequest("hi", "telegram", null, null))
        ).isInstanceOf(OpenClawException.class);
    }

    private ChatCommandService newService() {
        final AttemptExecutor attempt = new AttemptExecutor(
            ProviderDispatcher.direct(new EchoMockProviderClient()), new HookRunner());
        final PiAgentRunner runner = new PiAgentRunner(lanes, new ActiveRunRegistry(), attempt);
        final AutoReplyPipeline pipeline = new AutoReplyPipeline(new InMemorySessionRepository(), runner);
        final ChannelRegistry registry = new ChannelRegistry(List.of(new ChannelAdapter() {
            @Override
            public String channelId() {
                return "web";
            }

            @Override
            public void deliver(final OutboundMessage outbound) {
            }
        }));
        return new ChatCommandService(pipeline, registry);
    }
}
