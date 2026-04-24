package com.openclaw.tools;

import java.util.Map;

/**
 * Tool SPI. A tool advertises its {@link #name()}, human-readable {@link #description()} and a JSON-schema
 * {@link #parameters()} — the exact shape expected by OpenAI/Gemini/Anthropic function-calling. The actual
 * work happens inside {@link #execute(ToolRequest, ToolContext)}.
 *
 * <h2>Registration model</h2>
 * Tools are discovered by the {@code ToolRegistry} (runtime module). Plugin authors implement this SPI and
 * register via {@code PluginContext.registerSingleton(...)}; the registry picks them up during plugin load.
 * Duplicate {@link #name()}s within the same registry are a {@link ToolErrorCode#TOOL_NAME_CONFLICT hard
 * rejection} (plan §08 plugin governance).
 *
 * <h2>Policy pipeline</h2>
 * A concrete tool is ALWAYS wrapped by the runtime's policy pipeline before being exposed to the model:
 * <ol>
 *   <li>Outer 5-step assembly (OwnerOnly → ToolPolicyPipeline → ParameterNormalizer → BeforeToolCallHook
 *       → AbortSignalWrapper)</li>
 *   <li>Inner 9-step policy pipeline (profile/provider/global/agent/sandbox/subagent allow-lists)</li>
 * </ol>
 * Implementations MUST remain stateless across calls (the runtime retries / parallelizes freely); any
 * session-scoped state belongs on {@link ToolContext#attributes()} or an external store.
 *
 * <h2>Cooperation with hooks</h2>
 * {@code before_tool_call} hooks can rewrite {@link ToolRequest#params()} — by the time {@code execute}
 * runs, {@link ToolRequest} already reflects the merged view. {@code after_tool_call} hooks are fired
 * fire-and-forget by the runtime regardless of success / failure.
 */
public interface Tool {

    /** Globally unique name (snake_case or dot-separated recommended, e.g. {@code "clock.now"}). */
    String name();

    /** Free-form human-readable description; surfaced in the provider's function-calling schema. */
    String description();

    /** JSON-Schema-shaped parameter map, consumed as-is by provider SDKs. */
    Map<String, Object> parameters();

    /** Execute one call. Implementations MUST honour {@code context.abortSignal()} where feasible. */
    ToolResult execute(ToolRequest request, ToolContext context);
}
