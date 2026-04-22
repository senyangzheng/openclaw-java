package com.openclaw.cli;

import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.commands.ChatCommandService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * {@code openclaw-java chat --text "hi"} — sends a single message through the auto-reply
 * pipeline and prints the reply. Intended as a smoke-test during M1; M2+ will add
 * {@code --stream}, history fetch, and multi-turn REPL.
 */
@Command(
    name = "chat",
    description = "Send a one-shot message through the auto-reply pipeline."
)
public class ChatCommand implements Callable<Integer> {

    private final ChatCommandService chatService;

    public ChatCommand(final ChatCommandService chatService) {
        this.chatService = chatService;
    }

    @Option(names = {"-t", "--text"}, required = true, description = "Message text to send.")
    String text;

    @Option(names = {"-c", "--channel"}, defaultValue = "web", description = "Channel id (default: ${DEFAULT-VALUE}).")
    String channel;

    @Option(names = {"-a", "--account"}, defaultValue = "anonymous", description = "Account id (default: ${DEFAULT-VALUE}).")
    String account;

    @Option(names = {"--conversation"}, defaultValue = "default", description = "Conversation id (default: ${DEFAULT-VALUE}).")
    String conversation;

    @Override
    public Integer call() {
        final OutboundMessage reply = chatService.chat(
            new ChatCommandService.ChatCommandRequest(text, channel, account, conversation)
        );
        System.out.println(reply.text());
        return 0;
    }
}
