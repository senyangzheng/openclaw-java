/**
 * Dual-layer command-lane runtime backing M3.3.
 *
 * <p>Public API:
 * <ul>
 *   <li>{@link com.openclaw.lanes.LaneDispatcher} — single-layer queue + virtual-thread executor</li>
 *   <li>{@link com.openclaw.lanes.SessionLaneCoordinator} — session (outer) × global (inner) orchestrator</li>
 *   <li>{@link com.openclaw.lanes.CommandLane} / {@link com.openclaw.lanes.LaneNames} — lane name helpers</li>
 *   <li>{@link com.openclaw.lanes.GlobalLaneConcurrency} / {@link com.openclaw.lanes.SessionLaneProperties}
 *       — configuration holders</li>
 *   <li>{@link com.openclaw.lanes.CommandLaneClearedException} — cancellation signal for queued tasks</li>
 * </ul>
 */
package com.openclaw.lanes;
