package com.openclaw.agents.core.hooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openclaw.providers.api.ChatMessage;

/**
 * Merge accumulator for the {@code before_agent_start} hook chain.
 *
 * <p>Merge rules (see {@code .cursor/plan/05-translation-conventions.md} §16 #4):
 * <ul>
 *   <li>{@code systemPrompt}: last-writer-wins across hooks</li>
 *   <li>{@code prependContext}: list concatenation, order preserved (early hook → early message)</li>
 *   <li>{@code modelOverride}: last-writer-wins</li>
 *   <li>{@code providerExtras}: shallow map merge (later keys win)</li>
 * </ul>
 *
 * <p>Delta keys recognized by {@link #merge(BeforeAgentStartMerge, Map)}:
 * {@code "systemPrompt": String}, {@code "prependContext": List<ChatMessage>},
 * {@code "modelOverride": String}, {@code "providerExtras": Map<String,Object>}.
 * Unknown keys are silently ignored to keep hook payloads forward-compatible.
 */
public record BeforeAgentStartMerge(String systemPrompt,
                                    List<ChatMessage> prependContext,
                                    String modelOverride,
                                    Map<String, Object> providerExtras) {

    public BeforeAgentStartMerge {
        prependContext = prependContext == null ? List.of() : List.copyOf(prependContext);
        providerExtras = providerExtras == null ? Map.of() : Map.copyOf(providerExtras);
    }

    public static BeforeAgentStartMerge empty() {
        return new BeforeAgentStartMerge(null, List.of(), null, Map.of());
    }

    public static BeforeAgentStartMerge merge(final BeforeAgentStartMerge acc,
                                              final Map<String, Object> delta) {
        if (delta == null || delta.isEmpty()) {
            return acc;
        }
        final String sys = pickString(delta, "systemPrompt", acc.systemPrompt());
        final String modelOv = pickString(delta, "modelOverride", acc.modelOverride());

        @SuppressWarnings("unchecked")
        final List<ChatMessage> nextPrepend =
                (List<ChatMessage>) delta.getOrDefault("prependContext", List.of());
        final List<ChatMessage> prepend = new ArrayList<>(acc.prependContext());
        prepend.addAll(nextPrepend);

        final Map<String, Object> extras = new HashMap<>(acc.providerExtras());
        @SuppressWarnings("unchecked")
        final Map<String, Object> ex =
                (Map<String, Object>) delta.getOrDefault("providerExtras", Map.of());
        extras.putAll(ex);

        return new BeforeAgentStartMerge(sys, prepend, modelOv, extras);
    }

    private static String pickString(final Map<String, Object> delta, final String key, final String fallback) {
        final Object v = delta.get(key);
        return (v instanceof String s) ? s : fallback;
    }

    /**
     * Produce the effective provider messages for this run by layering systemPrompt (if any) → prependContext
     * → history → trailing user message. Duplicate suppression: the user message is appended only if it is not
     * already the last history entry (the caller may have {@code append}ed it before invoking the hook).
     */
    public List<ChatMessage> buildEffectiveMessages(final List<ChatMessage> history,
                                                    final ChatMessage userMessage) {
        final List<ChatMessage> effectiveHistory = history == null ? List.of() : history;
        final List<ChatMessage> out = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            out.add(ChatMessage.system(systemPrompt));
        }
        out.addAll(prependContext);
        out.addAll(effectiveHistory);
        if (userMessage != null && !containsTrailingUserTurn(effectiveHistory, userMessage)) {
            out.add(userMessage);
        }
        return List.copyOf(out);
    }

    private static boolean containsTrailingUserTurn(final List<ChatMessage> history, final ChatMessage msg) {
        if (history.isEmpty()) {
            return false;
        }
        final ChatMessage last = history.get(history.size() - 1);
        return last.equals(msg);
    }
}
