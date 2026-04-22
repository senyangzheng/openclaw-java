package com.openclaw.gateway.core;

import com.openclaw.autoreply.AutoReplyPipeline;
import com.openclaw.channels.core.ChannelRegistry;
import com.openclaw.gateway.core.methods.ChannelsListMethodHandler;
import com.openclaw.gateway.core.methods.ChatHistoryMethodHandler;
import com.openclaw.gateway.core.methods.ChatSendMethodHandler;
import com.openclaw.gateway.core.methods.PingMethodHandler;
import com.openclaw.sessions.SessionRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(OpenClawGatewayCoreAutoConfiguration.GatewayProperties.class)
public class OpenClawGatewayCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthGuard authGuard(final GatewayProperties props) {
        return new MockAuthGuard(props.authToken());
    }

    @Bean
    @ConditionalOnMissingBean
    public MethodDispatcher methodDispatcher(final List<MethodHandler> handlers, final AuthGuard authGuard) {
        return new MethodDispatcher(handlers, authGuard);
    }

    @Bean
    public ChatSendMethodHandler chatSendMethodHandler(final AutoReplyPipeline pipeline) {
        return new ChatSendMethodHandler(pipeline);
    }

    @Bean
    public ChatHistoryMethodHandler chatHistoryMethodHandler(final SessionRepository sessions) {
        return new ChatHistoryMethodHandler(sessions);
    }

    @Bean
    public ChannelsListMethodHandler channelsListMethodHandler(final ChannelRegistry registry) {
        return new ChannelsListMethodHandler(registry);
    }

    @Bean
    public PingMethodHandler pingMethodHandler() {
        return new PingMethodHandler();
    }

    @ConfigurationProperties(prefix = "openclaw.gateway")
    public record GatewayProperties(String authToken) {
    }
}
