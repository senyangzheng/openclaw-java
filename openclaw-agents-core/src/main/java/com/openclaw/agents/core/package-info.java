/**
 * Agent runtime skeleton backing M3.1.
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link com.openclaw.agents.core.AgentRunState} — run state machine (IDLE → QUEUED_SESSION →
 *       QUEUED_GLOBAL → ATTEMPTING → STREAMING → COMPLETED / FAILED)</li>
 *   <li>{@link com.openclaw.agents.core.AgentRunHandle} — per-run mutable handle (state + abort flag)</li>
 *   <li>{@link com.openclaw.agents.core.SubscribeState} — event multiplexer + {@code AgentEventSink} accumulator</li>
 *   <li>{@link com.openclaw.agents.core.ActiveRunRegistry} — per-session mutex with {@code clearIfMatches}</li>
 *   <li>{@link com.openclaw.agents.core.AttemptExecutor} — single attempt: before_agent_start hook → provider →
 *       translate → run_agent_end hook</li>
 *   <li>{@link com.openclaw.agents.core.PiAgentRunner} — top-level entry: mutex + lane scheduling + state drive</li>
 * </ul>
 *
 * <p>Deferred to later milestones: context-window guard (M3.2), tool policy pipeline (M3.2 / M3.4),
 * compaction retry (M3.2), model fallback (M3.5), subagents (M3.6).
 */
package com.openclaw.agents.core;
