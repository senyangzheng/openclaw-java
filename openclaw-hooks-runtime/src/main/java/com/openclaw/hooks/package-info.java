/**
 * Standalone Hook Runner backing M3.6.
 *
 * <p>Public API:
 * <ul>
 *   <li>{@link com.openclaw.hooks.HookRunner} — registration + parallel/sequential dispatch</li>
 *   <li>{@link com.openclaw.hooks.HookNames} — canonical injection-point names</li>
 *   <li>{@link com.openclaw.hooks.HookOutcome} — three-state result (modify/block/shortCircuit)</li>
 *   <li>{@link com.openclaw.hooks.HookContext} / {@link com.openclaw.hooks.ModifyingHookHandler} /
 *       {@link com.openclaw.hooks.VoidHookHandler} — handler SPI</li>
 *   <li>{@link com.openclaw.hooks.HookBlockedException} — propagated when a block hook fires</li>
 *   <li>{@link com.openclaw.hooks.HookDiagnostics} — registration / runtime incident log</li>
 * </ul>
 */
package com.openclaw.hooks;
