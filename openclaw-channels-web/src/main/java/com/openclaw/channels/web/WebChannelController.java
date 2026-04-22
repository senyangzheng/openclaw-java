package com.openclaw.channels.web;

import com.alibaba.fastjson2.JSON;
import com.openclaw.autoreply.AutoReplyPipeline;
import com.openclaw.channels.core.InboundMessage;
import com.openclaw.channels.core.OutboundMessage;
import com.openclaw.common.util.Strings;
import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.routing.RoutingKey;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound HTTP entry point for the Web channel. Accepts:
 *
 * <pre>
 * POST /api/channels/web/messages
 * {
 *   "text": "hello",
 *   "channelId": "web",          // optional, defaults to "web"
 *   "accountId": "anonymous",    // optional
 *   "conversationId": "default"  // optional
 * }
 * </pre>
 *
 * Response is the {@link OutboundMessage} serialized as JSON (via the project-wide
 * Fastjson2 converter registered in {@code openclaw-bootstrap}).
 */
@RestController
@RequestMapping("/api/channels/web")
@Validated
public class WebChannelController {

    private final AutoReplyPipeline pipeline;
    private final WebChannelAdapter channel;

    public WebChannelController(final AutoReplyPipeline pipeline, final WebChannelAdapter channel) {
        this.pipeline = pipeline;
        this.channel = channel;
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> postMessage(@RequestBody final InboundRequest body) {
        final String channelId = Strings.defaultIfBlank(body.channelId(), WebChannelAdapter.CHANNEL_ID);
        final String accountId = Strings.defaultIfBlank(body.accountId(), "anonymous");
        final String conversationId = Strings.defaultIfBlank(body.conversationId(), "default");

        final InboundMessage inbound = new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of(channelId, accountId, conversationId),
            body.text(),
            null,
            null
        );

        final OutboundMessage reply = pipeline.handle(inbound);
        channel.deliver(reply);

        return ResponseEntity.ok(Map.of(
            "messageId", reply.messageId(),
            "replyToMessageId", reply.replyToMessageId(),
            "text", reply.text(),
            "channelId", channelId,
            "accountId", accountId,
            "conversationId", conversationId
        ));
    }

    /**
     * Streaming variant of {@code POST /messages}. Returns a {@code text/event-stream}
     * with one SSE frame per {@link ChatChunkEvent}:
     *
     * <pre>
     * event: delta
     * data: {"content":"Hello"}
     *
     * event: done
     * data: {"finishReason":"STOP","promptTokens":5,"completionTokens":2,"totalTokens":7}
     * </pre>
     *
     * Clients should stop reading after {@code event: done} (or {@code event: error}).
     * Spring MVC turns the returned {@code Flux} into a streaming response thanks to
     * {@code reactor-core} on the classpath — no WebFlux required.
     */
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamMessage(@RequestBody final InboundRequest body) {
        final String channelId = Strings.defaultIfBlank(body.channelId(), WebChannelAdapter.CHANNEL_ID);
        final String accountId = Strings.defaultIfBlank(body.accountId(), "anonymous");
        final String conversationId = Strings.defaultIfBlank(body.conversationId(), "default");

        final InboundMessage inbound = new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of(channelId, accountId, conversationId),
            body.text(),
            null,
            null
        );

        return pipeline.streamHandle(inbound)
            .map(WebChannelController::toSse);
    }

    private static ServerSentEvent<String> toSse(final ChatChunkEvent event) {
        return switch (event) {
            case ChatChunkEvent.Delta d -> ServerSentEvent.<String>builder()
                .event("delta")
                .data(JSON.toJSONString(Map.of("content", d.content())))
                .build();
            case ChatChunkEvent.ToolCall tc -> ServerSentEvent.<String>builder()
                .event("tool_call")
                .data(JSON.toJSONString(Map.of(
                    "id", tc.chunk().id(),
                    "index", tc.chunk().index(),
                    "name", tc.chunk().name() == null ? "" : tc.chunk().name(),
                    "argumentsDelta", tc.chunk().argumentsDelta())))
                .build();
            case ChatChunkEvent.Done done -> {
                final Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("finishReason", done.reason().name());
                payload.put("promptTokens", done.usage().promptTokens());
                payload.put("completionTokens", done.usage().completionTokens());
                payload.put("totalTokens", done.usage().totalTokens());
                yield ServerSentEvent.<String>builder()
                    .event("done")
                    .data(JSON.toJSONString(payload))
                    .build();
            }
            case ChatChunkEvent.Error err -> ServerSentEvent.<String>builder()
                .event("error")
                .data(JSON.toJSONString(Map.of("code", err.code(), "message", err.message())))
                .build();
        };
    }

    public record InboundRequest(
        @NotBlank String text,
        String channelId,
        String accountId,
        String conversationId
    ) {
    }
}
