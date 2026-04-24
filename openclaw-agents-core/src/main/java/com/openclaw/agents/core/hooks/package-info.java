/**
 * Public hook event + merger types produced by the agent runtime, consumed by:
 * <ul>
 *   <li>{@link com.openclaw.agents.core.AttemptExecutor} — fires {@code before_agent_start} /
 *       {@code run_agent_end}</li>
 *   <li>{@code openclaw-auto-reply} — short-circuits the LLM path when a hook returns
 *       {@link com.openclaw.hooks.HookOutcome.ShortCircuit} (the new home of {@code /slash commands},
 *       replacing the deleted {@code ChatCommand} SPI)</li>
 *   <li>Plugin handlers — register via
 *       {@link com.openclaw.hooks.HookRunner#registerModifying(String, String, int,
 *       com.openclaw.hooks.ModifyingHookHandler)}</li>
 * </ul>
 */
package com.openclaw.agents.core.hooks;
