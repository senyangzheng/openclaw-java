package com.openclaw.cli;

import com.openclaw.channels.core.ChannelRegistry;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code openclaw-java channels} — lists the channel ids currently registered.
 */
@Command(
    name = "channels",
    description = "List registered channels."
)
public class ChannelsCommand implements Callable<Integer> {

    private final ChannelRegistry registry;

    public ChannelsCommand(final ChannelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Integer call() {
        for (final String id : registry.channelIds()) {
            System.out.println(id);
        }
        return 0;
    }
}
