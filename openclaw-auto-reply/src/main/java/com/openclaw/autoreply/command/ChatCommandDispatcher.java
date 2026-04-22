package com.openclaw.autoreply.command;

import com.openclaw.channels.core.InboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Fans the inbound message across every {@link ChatCommand} bean in order and
 * returns the first successful reply. Designed as the front-door of
 * {@link com.openclaw.autoreply.AutoReplyPipeline}: if it produces a result,
 * the LLM path is skipped.
 *
 * <h2>Lazy resolution</h2>
 * The supplier is re-queried on every {@link #tryHandle(InboundMessage)} call.
 * This is deliberate: plugins register their {@link ChatCommand} beans inside
 * {@code onLoad}, which fires on {@code ContextRefreshedEvent} — AFTER this
 * dispatcher bean has been constructed. A snapshot-at-construction dispatcher
 * would silently miss them. The cost is a list-stream allocation per inbound
 * request; negligible compared to the provider round-trip it's fronting.
 *
 * <h2>Error handling</h2>
 * A command's {@link ChatCommand#matches(InboundMessage)} is expected to be
 * total — exceptions raise to a WARN and the command is treated as a non-match.
 * A command's {@link ChatCommand#handle(InboundMessage)} is allowed to throw;
 * the dispatcher logs a WARN and returns {@link Optional#empty()} so the
 * pipeline falls back to the LLM. This matches the "broken plugin never
 * crashes the runtime" guarantee enforced at plugin load time.
 */
public final class ChatCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatCommandDispatcher.class);

    private final Supplier<List<ChatCommand>> commandsSupplier;

    /** Static-list constructor for tests and back-compat. */
    public ChatCommandDispatcher(final List<ChatCommand> commands) {
        this(() -> List.copyOf(Objects.requireNonNull(commands, "commands")));
    }

    /**
     * Lazy constructor — the supplier is consulted on every dispatch so
     * commands registered after bean creation (e.g. by plugins in
     * {@code onLoad}) become visible without any refresh gymnastics.
     */
    public ChatCommandDispatcher(final Supplier<List<ChatCommand>> commandsSupplier) {
        this.commandsSupplier = Objects.requireNonNull(commandsSupplier, "commandsSupplier");
    }

    /** @return the reply body when a command matched + handled; empty otherwise. */
    public Optional<DispatchResult> tryHandle(final InboundMessage inbound) {
        Objects.requireNonNull(inbound, "inbound");
        final List<ChatCommand> commands = sorted(commandsSupplier.get());
        if (commands.isEmpty()) {
            return Optional.empty();
        }
        for (ChatCommand command : commands) {
            final boolean matched;
            try {
                matched = command.matches(inbound);
            } catch (RuntimeException ex) {
                log.warn("chat-command.matches.failed name={} cause={}",
                    command.name(), ex.toString());
                continue;
            }
            if (!matched) {
                continue;
            }
            try {
                final String reply = command.handle(inbound);
                if (reply == null) {
                    continue;
                }
                log.info("chat-command.dispatched name={} replyLen={}", command.name(), reply.length());
                return Optional.of(new DispatchResult(command.name(), reply));
            } catch (RuntimeException ex) {
                log.warn("chat-command.handle.failed name={} cause={} — falling back to LLM",
                    command.name(), ex.toString());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Snapshot of registered command names, in dispatch order. Useful for diagnostics. */
    public List<String> names() {
        return sorted(commandsSupplier.get()).stream().map(ChatCommand::name).toList();
    }

    private static List<ChatCommand> sorted(final List<ChatCommand> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
            .sorted(Comparator.comparingInt(ChatCommand::order).thenComparing(ChatCommand::name))
            .toList();
    }

    /** Successful dispatch outcome: which command fired + what to reply. */
    public record DispatchResult(String commandName, String reply) {
    }
}
