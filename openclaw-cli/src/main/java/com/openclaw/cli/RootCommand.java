package com.openclaw.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Top-level CLI command.
 *
 * <p><b>Naming contract</b>: the shell command MUST be {@code openclaw-java}
 * (never plain {@code openclaw}) — see
 * {@code .cursor/plan/05-translation-conventions.md} §11. That name is reflected here,
 * in {@code bin/openclaw-java}, and in the Docker {@code ENTRYPOINT}.
 */
@Command(
    name = "openclaw-java",
    mixinStandardHelpOptions = true,
    version = "openclaw-java 0.1.0-SNAPSHOT",
    description = "OpenClaw Java edition — CLI entry point.",
    subcommands = {
        ChatCommand.class,
        ChannelsCommand.class
    }
)
public class RootCommand implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose logging (sets root log level to DEBUG).")
    boolean verbose;

    @Override
    public void run() {
        System.out.println("openclaw-java — use 'openclaw-java --help' to see available commands.");
    }
}
