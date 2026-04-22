package com.openclaw.commands;

import com.openclaw.autoreply.AutoReplyPipeline;
import com.openclaw.channels.core.ChannelAdapter;
import com.openclaw.channels.core.ChannelRegistry;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.common.error.OpenClawException;
import com.openclaw.providers.api.mock.EchoMockProviderClient;
import com.openclaw.sessions.InMemorySessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatCommandServiceTest {

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

    private static ChatCommandService newService() {
        final AutoReplyPipeline pipeline = new AutoReplyPipeline(
            new InMemorySessionRepository(), new EchoMockProviderClient()
        );
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
