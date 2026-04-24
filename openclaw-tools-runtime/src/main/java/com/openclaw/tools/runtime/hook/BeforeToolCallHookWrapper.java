package com.openclaw.tools.runtime.hook;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.hooks.HookBlockedException;
import com.openclaw.hooks.HookContext;
import com.openclaw.hooks.HookNames;
import com.openclaw.hooks.HookRunner;
import com.openclaw.hooks.ModifyingHookResult;
import com.openclaw.tools.Tool;
import com.openclaw.tools.ToolContext;
import com.openclaw.tools.ToolErrorCode;
import com.openclaw.tools.ToolRequest;
import com.openclaw.tools.ToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a {@link Tool} so every call fires {@link HookNames#BEFORE_TOOL_CALL} before reaching the executor.
 * Mirrors openclaw-ts {@code wrapToolWithBeforeToolCallHook} (agents/pi-tools.before-tool-call.ts).
 *
 * <h2>Multi-plugin merge rules</h2>
 * Per plan §13.2 step 1, the modifying hook accumulator is reduced with <b>last-write-wins</b> on three
 * keys: {@code params}, {@code block}, {@code blockReason} ({@code next ?? acc}). Single-plugin merge of
 * the chosen {@code params} with the original is {@code {...originalParams, ...hookParams}}.
 *
 * <h2>Adjusted params store</h2>
 * On a successful merge the rewritten {@code params} are stashed into {@link AdjustedParamsStore} keyed by
 * {@code toolCallId} so the later {@code after_tool_call} emitter can observe the post-hook view.
 *
 * <h2>{@code BEFORE_TOOL_CALL_WRAPPED} marker</h2>
 * The wrapped tool's {@link Tool#name()} is identical to the delegate's (so it slots into the registry as
 * the same logical tool); we use a static sentinel class identity check
 * ({@link #isAlreadyWrapped(Tool)}) rather than an unenumerable property so downstream assemblers can
 * detect re-wrap attempts and skip them — matches the {@code BEFORE_TOOL_CALL_WRAPPED} sentinel in ts.
 */
public final class BeforeToolCallHookWrapper {

    private static final Logger log = LoggerFactory.getLogger(BeforeToolCallHookWrapper.class);

    /** Marker key used when a hook outcome delta wants to block the call. */
    public static final String DELTA_BLOCK = "block";
    /** Marker key used when a hook outcome delta carries a block reason. */
    public static final String DELTA_BLOCK_REASON = "blockReason";
    /** Marker key used when a hook outcome delta rewrites params. */
    public static final String DELTA_PARAMS = "params";

    private BeforeToolCallHookWrapper() {
    }

    /** Idempotent: wrapping an already-wrapped tool returns it unchanged. */
    public static Tool wrap(final Tool tool,
                            final HookRunner hookRunner,
                            final AdjustedParamsStore store) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(hookRunner, "hookRunner");
        Objects.requireNonNull(store, "store");
        if (isAlreadyWrapped(tool)) {
            return tool;
        }
        return new Wrapped(tool, hookRunner, store);
    }

    public static boolean isAlreadyWrapped(final Tool tool) {
        return tool instanceof Wrapped;
    }

    /** Internal: unwraps one level for introspection / tests. */
    public static Tool unwrap(final Tool tool) {
        return tool instanceof Wrapped w ? w.delegate : tool;
    }

    /** Accumulator applied by {@link HookRunner#runModifyingHook}. */
    private static final class Accumulator {
        Map<String, Object> params;
        Boolean block;
        String blockReason;

        Accumulator merged(final Map<String, Object> delta) {
            if (delta == null || delta.isEmpty()) {
                return this;
            }
            if (delta.containsKey(DELTA_PARAMS)) {
                final Object raw = delta.get(DELTA_PARAMS);
                if (raw instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> typed = (Map<String, Object>) m;
                    this.params = Map.copyOf(typed);
                }
            }
            if (delta.containsKey(DELTA_BLOCK)) {
                final Object raw = delta.get(DELTA_BLOCK);
                if (raw instanceof Boolean b) {
                    this.block = b;
                }
            }
            if (delta.containsKey(DELTA_BLOCK_REASON)) {
                final Object raw = delta.get(DELTA_BLOCK_REASON);
                if (raw instanceof String s) {
                    this.blockReason = s;
                }
            }
            return this;
        }
    }

    private static final class Wrapped implements Tool {
        private final Tool delegate;
        private final HookRunner hookRunner;
        private final AdjustedParamsStore store;

        Wrapped(final Tool delegate, final HookRunner hookRunner, final AdjustedParamsStore store) {
            this.delegate = delegate;
            this.hookRunner = hookRunner;
            this.store = store;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public Map<String, Object> parameters() {
            return delegate.parameters();
        }

        @Override
        public ToolResult execute(final ToolRequest request, final ToolContext context) {
            final BeforeToolCallEvent evt = new BeforeToolCallEvent(
                    request.toolName(), request.toolCallId(), request.params(), context);
            final Accumulator acc = new Accumulator();
            final ModifyingHookResult<Accumulator> result;
            try {
                result = hookRunner.runModifyingHook(
                        HookNames.BEFORE_TOOL_CALL,
                        evt,
                        HookContext.of(HookNames.BEFORE_TOOL_CALL),
                        acc,
                        (a, delta) -> a.merged(delta));
            } catch (HookBlockedException ex) {
                throw new OpenClawException(
                        ToolErrorCode.TOOL_BLOCKED_BY_HOOK, ex.blockReason(), ex);
            }

            // ShortCircuit on before_tool_call is intentionally unsupported (tool calls always round-trip
            // through an executor; "skip the tool and canned-reply" belongs on before_agent_start).
            final Accumulator finalAcc = result.accumulator();
            if (Boolean.TRUE.equals(finalAcc.block)) {
                final String reason = finalAcc.blockReason == null ? "blocked" : finalAcc.blockReason;
                throw new OpenClawException(ToolErrorCode.TOOL_BLOCKED_BY_HOOK, reason);
            }

            final Map<String, Object> effectiveParams;
            if (finalAcc.params != null) {
                // Single-plugin merge: {...original, ...hookParams}
                final Map<String, Object> merged = new LinkedHashMap<>(request.params());
                merged.putAll(finalAcc.params);
                effectiveParams = Map.copyOf(merged);
                store.put(request.toolCallId(), effectiveParams);
                log.debug("tools.before_tool_call.rewrite name={} toolCallId={} keys={}",
                        request.toolName(), request.toolCallId(), effectiveParams.keySet());
            } else {
                effectiveParams = request.params();
            }
            final ToolRequest effective = request.withParams(effectiveParams);
            return delegate.execute(effective, context);
        }

        /** Unused — kept to silence "never used" warnings if introspection reads {@code toString}. */
        @Override
        public String toString() {
            final Map<String, Object> marker = new HashMap<>();
            marker.put("wrapped", delegate.name());
            return "BeforeToolCallHookWrapper" + marker;
        }
    }
}
