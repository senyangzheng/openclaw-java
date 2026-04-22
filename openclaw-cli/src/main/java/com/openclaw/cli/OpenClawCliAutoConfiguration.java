package com.openclaw.cli;

import com.openclaw.channels.core.ChannelRegistry;
import com.openclaw.commands.ChatCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine.IFactory;

/**
 * Wires the Picocli root command and subcommand beans. The {@link IFactory} itself
 * is supplied by {@code picocli-spring-boot-starter}'s own auto-configuration;
 * we only add our command beans because the main application ({@code com.openclaw.bootstrap})
 * does not scan the CLI package.
 */
@AutoConfiguration
public class OpenClawCliAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RootCommand rootCommand() {
        return new RootCommand();
    }

    @Bean
    public ChatCommand chatCommand(final ChatCommandService chatService) {
        return new ChatCommand(chatService);
    }

    @Bean
    public ChannelsCommand channelsCommand(final ChannelRegistry registry) {
        return new ChannelsCommand(registry);
    }

    @Bean
    public OpenClawCliRunner openClawCliRunner(final RootCommand root,
                                               final IFactory factory,
                                               final ConfigurableApplicationContext context,
                                               @Value("${openclaw.cli.enabled:false}") final boolean cliEnabled) {
        return new OpenClawCliRunner(root, factory, context, cliEnabled);
    }
}
