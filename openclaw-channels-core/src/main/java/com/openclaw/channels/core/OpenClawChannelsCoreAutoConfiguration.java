package com.openclaw.channels.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class OpenClawChannelsCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ChannelRegistry channelRegistry(final List<ChannelAdapter> channels) {
        return new ChannelRegistry(channels);
    }
}
