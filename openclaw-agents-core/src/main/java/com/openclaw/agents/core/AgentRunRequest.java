package com.openclaw.agents.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.openclaw.lanes.CommandLane;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.sessions.SessionKey;

/**
 * Immutable request envelope passed from callers (AutoReply / Gateway / Subagent) into the agent runtime.
 *
 * <p>M3.1 scope — this carries only the minimum fields needed to drive the state machine and provider call.
 * M3.2+ will add {@code tools / skills / memoryPolicy / thinkingLevel / maxTokens / providerPreference}.
 *
 * @param sessionKey   the session under which this run executes (used for lane + registry + persistence)
 * @param globalLane   category cap (default {@link CommandLane#MAIN}); non-null
 * @param userMessage  the user turn that triggered the run (null allowed for synthetic runs)
 * @param history      preceding messages already persisted in the session; ordering preserved verbatim
 * @param metadata     non-PII diagnostic metadata (e.g. {@code requestId}, {@code clientHint})
 */
public record AgentRunRequest(SessionKey sessionKey,
                              CommandLane globalLane,
                              ChatMessage userMessage,
                              List<ChatMessage> history,
                              Map<String, Object> metadata) {

    public AgentRunRequest {
        Objects.requireNonNull(sessionKey, "sessionKey");
        globalLane = globalLane == null ? CommandLane.MAIN : globalLane;
        history = history == null ? List.of() : List.copyOf(history);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentRunRequest of(final SessionKey sessionKey,
                                     final ChatMessage userMessage,
                                     final List<ChatMessage> history) {
        return new AgentRunRequest(sessionKey, CommandLane.MAIN, userMessage, history, Map.of());
    }
}
