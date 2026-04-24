package com.openclaw.agents.core.hooks;

import java.util.Objects;

import com.openclaw.sessions.SessionKey;

/**
 * Immutable event payload delivered to every {@code run_agent_end} hook handler. Fires exactly once per run,
 * asynchronously, after the attempt terminates (success, error, or short-circuit).
 *
 * @param runId       opaque run identifier (matches {@code AgentRunHandle#id})
 * @param sessionKey  session under which this run executed
 * @param status      {@code "ok"}, {@code "error"}, {@code "short-circuit"}
 * @param error       non-null iff {@code status="error"}
 */
public record RunAgentEndEvent(String runId,
                               SessionKey sessionKey,
                               String status,
                               Throwable error) {

    public RunAgentEndEvent {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(sessionKey, "sessionKey");
        status = status == null ? "ok" : status;
    }
}
