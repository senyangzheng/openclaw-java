package com.openclaw.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * When activated (by default when {@code openclaw.cli.enabled=true}), runs the Picocli
 * command line with the process arguments, then exits with Picocli's exit code.
 * <p>
 * The full Spring web server is only started when {@code openclaw.cli.enabled} is
 * {@code false} (or not set explicitly), which is the normal server profile.
 */
public class OpenClawCliRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenClawCliRunner.class);

    private final RootCommand rootCommand;
    private final IFactory factory;
    private final ConfigurableApplicationContext context;
    private final boolean cliEnabled;

    private int exitCode = 0;

    public OpenClawCliRunner(final RootCommand rootCommand,
                             final IFactory factory,
                             final ConfigurableApplicationContext context,
                             @Value("${openclaw.cli.enabled:false}") final boolean cliEnabled) {
        this.rootCommand = rootCommand;
        this.factory = factory;
        this.context = context;
        this.cliEnabled = cliEnabled;
    }

    @Override
    public void run(final ApplicationArguments args) {
        if (!cliEnabled) {
            return;
        }
        try {
            exitCode = new CommandLine(rootCommand, factory).execute(args.getSourceArgs());
        } catch (RuntimeException e) {
            log.error("cli.execute.failed", e);
            exitCode = 1;
        } finally {
            context.close();
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
