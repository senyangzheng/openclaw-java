package com.openclaw.autoreply;

import com.openclaw.autoreply.command.ChatCommandDispatcher;
import com.openclaw.channels.core.InboundMessage;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.logging.MdcKeys;
import com.openclaw.logging.MdcScope;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.sessions.Session;
import com.openclaw.sessions.SessionKey;
import com.openclaw.sessions.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.Optional;

/**
 * M1 happy-path pipeline:
 * <pre>
 *   inbound → [chatCommand?] → session.append(user) → provider.chat → session.append(assistant) → outbound
 * </pre>
 *
 * <p>A non-empty {@link ChatCommandDispatcher} lets plugins (or ops scripts)
 * register front-of-pipeline hooks that SHORT-CIRCUIT the LLM path — useful for
 * deterministic replies (e.g. {@code /hello alice}) and zero-cost ops commands.
 * The dispatcher is optional: constructing without one preserves the plain
 * inbound → provider behaviour used by existing tests.
 *
 * <p>No retry / no tool-calls; see M3 for the full Agent runtime.
 */
public class AutoReplyPipeline {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyPipeline.class);

    private final SessionRepository sessions;
    private final ProviderClient provider;
    private final ChatCommandDispatcher commandDispatcher;

    /** Back-compat constructor — pipeline without any front-of-line commands. */
    public AutoReplyPipeline(final SessionRepository sessions, final ProviderClient provider) {
        this(sessions, provider, new ChatCommandDispatcher(java.util.List.of()));
    }

    public AutoReplyPipeline(final SessionRepository sessions,
                             final ProviderClient provider,
                             final ChatCommandDispatcher commandDispatcher) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.commandDispatcher = Objects.requireNonNull(commandDispatcher, "commandDispatcher");
    }

    public OutboundMessage handle(final InboundMessage inbound) {
        Objects.requireNonNull(inbound, "inbound");
        final SessionKey sessionKey = inbound.routingKey().toSessionKey();

        try (var ignored = MdcScope.of(MdcKeys.CHANNEL, inbound.routingKey().account().channelId())
            .with(MdcKeys.SESSION_ID, sessionKey.asString())
            .with(MdcKeys.PROVIDER, provider.providerId())) {

            log.info("auto-reply.inbound messageId={} textLen={}", inbound.messageId(), inbound.text().length());

            final Session session = sessions.loadOrCreate(sessionKey);
            session.append(ChatMessage.user(inbound.text()));

            final Optional<ChatCommandDispatcher.DispatchResult> dispatched =
                commandDispatcher.tryHandle(inbound);
            if (dispatched.isPresent()) {
                final String reply = dispatched.get().reply();
                session.append(ChatMessage.assistant(reply));
                sessions.save(session);
                log.info("auto-reply.outbound.command command={} replyLen={}",
                    dispatched.get().commandName(), reply.length());
                return OutboundMessage.replyTo(inbound, reply);
            }

            final ChatResponse response = provider.chat(
                ChatRequest.of(null, session.messages())
            );

            session.append(ChatMessage.assistant(response.content()));
            sessions.save(session);

            log.info("auto-reply.outbound finish={} elapsedMs={}",
                response.finishReason(), response.elapsed().toMillis());

            return OutboundMessage.replyTo(inbound, response.content());
        }
    }

    /**
     * Streaming counterpart of {@link #handle(InboundMessage)}. Forwards token-level
     * {@link ChatChunkEvent}s to the caller (typically an SSE endpoint) while transparently
     * accumulating the assistant's full content into the {@link Session} — persistence only
     * fires once the stream terminates, so a mid-stream disconnect leaves the session in a
     * self-consistent state (user turn appended, assistant turn discarded).
     */
    public Flux<ChatChunkEvent> streamHandle(final InboundMessage inbound) {
        Objects.requireNonNull(inbound, "inbound");
        final SessionKey sessionKey = inbound.routingKey().toSessionKey();
        final String channelId = inbound.routingKey().account().channelId();
        final Session session = sessions.loadOrCreate(sessionKey);
        session.append(ChatMessage.user(inbound.text()));

        // Command short-circuit: emit the full reply as a single Delta + Done.
        // Keeps the SSE shape identical to the LLM path so clients don't branch.
        final Optional<ChatCommandDispatcher.DispatchResult> dispatched =
            commandDispatcher.tryHandle(inbound);
        if (dispatched.isPresent()) {
            final String reply = dispatched.get().reply();
            final String commandName = dispatched.get().commandName();
            session.append(ChatMessage.assistant(reply));
            sessions.save(session);
            log.info("auto-reply.stream.command command={} replyLen={}", commandName, reply.length());
            return Flux.just(
                (ChatChunkEvent) new ChatChunkEvent.Delta(reply),
                ChatChunkEvent.Done.STOP);
        }

        final StringBuilder assistant = new StringBuilder();

        return Flux.defer(() -> {
                try (var ignored = MdcScope.of(MdcKeys.CHANNEL, channelId)
                    .with(MdcKeys.SESSION_ID, sessionKey.asString())
                    .with(MdcKeys.PROVIDER, provider.providerId())) {
                    log.info("auto-reply.stream.begin messageId={} textLen={}",
                        inbound.messageId(), inbound.text().length());
                    return provider.streamChat(ChatRequest.of(null, session.messages()));
                }
            })
            .doOnNext(event -> {
                if (event instanceof ChatChunkEvent.Delta(String content)) {
                    assistant.append(content);
                }
            })
            .doOnComplete(() -> {
                try (var ignored = MdcScope.of(MdcKeys.CHANNEL, channelId)
                    .with(MdcKeys.SESSION_ID, sessionKey.asString())
                    .with(MdcKeys.PROVIDER, provider.providerId())) {
                    if (assistant.length() > 0) {
                        session.append(ChatMessage.assistant(assistant.toString()));
                        sessions.save(session);
                    }
                    log.info("auto-reply.stream.end assistantLen={}", assistant.length());
                }
            })
            .doOnError(err -> log.warn("auto-reply.stream.error sessionId={}",
                sessionKey.asString(), err));
    }
}
