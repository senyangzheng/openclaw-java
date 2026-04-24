package com.openclaw.agents.core.hooks;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.openclaw.providers.api.ChatMessage;
import com.openclaw.sessions.SessionKey;

/**
 * Immutable event payload delivered to every {@code before_agent_start} hook handler.
 *
 * <p>Mirrors the openclaw-ts {@code RunAgentStartPayload}. Handlers inspect the user turn and history and
 * return one of {@link com.openclaw.hooks.HookOutcome.Modify}, {@link com.openclaw.hooks.HookOutcome.Block}, or
 * {@link com.openclaw.hooks.HookOutcome.ShortCircuit}:
 * <ul>
 *   <li><b>Modify</b> — return a delta that the merger ({@link BeforeAgentStartMerge}) folds into the outgoing
 *       provider request (systemPrompt / prependContext / providerExtras / modelOverride).</li>
 *   <li><b>Block</b> — refuse to run the agent (e.g. over-quota). Surfaced to the caller as
 *       {@link com.openclaw.hooks.HookBlockedException}.</li>
 *   <li><b>ShortCircuit(reply)</b> — replace the LLM output with {@code reply} verbatim. This is the canonical
 *       replacement for the legacy {@code ChatCommand} SPI: user slash-commands like {@code /hello alice} become
 *       a high-priority short-circuit hook that returns a deterministic greeting, skipping the provider.</li>
 * </ul>
 *
 * @param sessionKey  current session (use for per-session memoization, MDC, audit)
 * @param userMessage the user turn that triggered this run; {@code null} only for synthetic/cron runs
 * @param history     messages already persisted in the session (read-only snapshot)
 * @param metadata    opaque request-scope metadata (never PII)
 */
public record BeforeAgentStartEvent(SessionKey sessionKey,
                                    ChatMessage userMessage,
                                    List<ChatMessage> history,
                                    Map<String, Object> metadata) {

    public BeforeAgentStartEvent {
        Objects.requireNonNull(sessionKey, "sessionKey");
        history = history == null ? List.of() : List.copyOf(history);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
