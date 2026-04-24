/**
 * Business-layer streaming primitives backing M3.1 / M3.2.
 *
 * <p>Public API:
 * <ul>
 *   <li>{@link com.openclaw.stream.AgentEvent} — sealed event union (Delta / Reasoning / ToolCall /
 *       ToolResult / Done / Error)</li>
 *   <li>{@link com.openclaw.stream.ChatChunkEventTranslator} — {@code ChatChunkEvent → AgentEvent} with
 *       tool-call arguments accumulation</li>
 *   <li>{@link com.openclaw.stream.AgentEventSink} — reactive-friendly accumulator + {@code foldBlocking} helper</li>
 * </ul>
 */
package com.openclaw.stream;
