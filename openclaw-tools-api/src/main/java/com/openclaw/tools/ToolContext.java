package com.openclaw.tools;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ambient context injected into {@link Tool#execute(ToolRequest, ToolContext)} by the tools runtime. Carries
 * identifiers the tool needs for logging / billing / sub-agent dispatch but does NOT carry the full agent
 * run state (tools are intentionally "dumb workers" that see only what they need).
 *
 * @param sessionId   opaque session id, matching {@code SessionKey.asString()} used by sessions module
 * @param agentId     optional agent id ({@code null} when running as the top-level agent);
 *                    {@code resolveAgentId()} etc. in ts-openclaw
 * @param senderIsOwner whether the initiator is the session owner — controls whether
 *                    {@link ToolErrorCode#TOOL_DENIED owner-only tools} are exposed
 * @param abortSignal optional cooperative-cancel signal; tools SHOULD poll it at long-running steps
 * @param attributes  free-form string-keyed attributes for telemetry / provenance
 */
public record ToolContext(String sessionId,
                          String agentId,
                          boolean senderIsOwner,
                          AbortSignal abortSignal,
                          Map<String, Object> attributes) {

    public ToolContext {
        Objects.requireNonNull(sessionId, "sessionId");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public Optional<String> agentIdOpt() {
        return Optional.ofNullable(agentId);
    }

    public Optional<AbortSignal> abortSignalOpt() {
        return Optional.ofNullable(abortSignal);
    }

    public static ToolContext of(final String sessionId) {
        return new ToolContext(sessionId, null, false, null, Map.of());
    }

    /**
     * Minimal cooperative-cancel hook; backed by an underlying Reactor {@code Disposable} / task abort flag
     * in the runtime. Tools that poll {@link #isAborted()} and exit early should return a
     * {@link ToolResult#fail(String)} with reason {@code "aborted"}.
     */
    @FunctionalInterface
    public interface AbortSignal {
        boolean isAborted();
    }
}
