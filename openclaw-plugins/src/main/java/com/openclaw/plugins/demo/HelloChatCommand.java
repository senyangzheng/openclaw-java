package com.openclaw.plugins.demo;

import com.openclaw.autoreply.command.ChatCommand;
import com.openclaw.channels.core.InboundMessage;

import java.util.Objects;

/**
 * Demo chat command registered by {@link HelloPlugin}. Listens for any message
 * whose text starts with {@code "/hello"} (case-sensitive, trimmed) and
 * produces a deterministic greeting via {@link HelloGreeter} — no LLM call.
 *
 * <p><b>Purpose</b>: end-to-end proof that a plugin-contributed bean can
 * participate in the Web chat pipeline. Try it with:
 * <pre>
 * curl -s -XPOST http://localhost:8080/api/channels/web/messages \
 *   -H 'Content-Type: application/json' \
 *   -d '{"accountId":"u1","conversationId":"c1","text":"/hello alice"}'
 * </pre>
 */
public class HelloChatCommand implements ChatCommand {

    /** Trigger prefix. Anything else is ignored and falls through to the LLM. */
    public static final String TRIGGER = "/hello";

    private final HelloGreeter greeter;

    public HelloChatCommand(final HelloGreeter greeter) {
        this.greeter = Objects.requireNonNull(greeter, "greeter");
    }

    @Override
    public String name() {
        return "hello";
    }

    @Override
    public boolean matches(final InboundMessage inbound) {
        final String text = inbound.text();
        if (text == null) {
            return false;
        }
        final String stripped = text.stripLeading();
        return stripped.equals(TRIGGER)
            || stripped.startsWith(TRIGGER + " ")
            || stripped.startsWith(TRIGGER + "\t");
    }

    @Override
    public String handle(final InboundMessage inbound) {
        final String stripped = inbound.text().stripLeading().substring(TRIGGER.length()).trim();
        final String name = stripped.isEmpty() ? "friend" : stripped;
        return greeter.greet(name);
    }

    @Override
    public int order() {
        return -100;
    }
}
