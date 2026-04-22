package com.openclaw.commands;

import com.openclaw.autoreply.AutoReplyPipeline;
import com.openclaw.channels.core.ChannelRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class OpenClawCommandsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ChatCommandService chatCommandService(final AutoReplyPipeline pipeline,
                                                 final ChannelRegistry channels) {
        return new ChatCommandService(pipeline, channels);
    }
}
