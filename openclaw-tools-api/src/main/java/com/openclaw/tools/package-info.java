/**
 * Tool SPI ({@link com.openclaw.tools.Tool} + {@link com.openclaw.tools.ToolRequest}
 * + {@link com.openclaw.tools.ToolResult} + {@link com.openclaw.tools.ToolContext}).
 *
 * <p>No runtime / hook / spring code lives here: {@code openclaw-tools-api} is plugin-facing and
 * intentionally dependency-free aside from {@code openclaw-common} (for the shared
 * {@link com.openclaw.common.error.ErrorCode} contract).
 *
 * <p>See {@code openclaw-tools-runtime} for the registry, policy pipeline, and hook wrappers.
 */
package com.openclaw.tools;
