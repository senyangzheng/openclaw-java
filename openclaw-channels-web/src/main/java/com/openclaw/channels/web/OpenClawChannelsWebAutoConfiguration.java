package com.openclaw.channels.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class OpenClawChannelsWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebChannelAdapter webChannelAdapter() {
        return new WebChannelAdapter();
    }
}
