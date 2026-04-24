package com.openclaw.tools.runtime.hook;

import java.util.HashMap;
import java.util.Map;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.hooks.HookNames;
import com.openclaw.hooks.HookOutcome;
import com.openclaw.hooks.HookRunner;
import com.openclaw.hooks.ModifyingHookHandler;
import com.openclaw.tools.Tool;
import com.openclaw.tools.ToolContext;
import com.openclaw.tools.ToolErrorCode;
import com.openclaw.tools.ToolRequest;
import com.openclaw.tools.ToolResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeforeToolCallHookWrapperTest {

    private HookRunner runner;
    private AdjustedParamsStore store;
    private RecordingTool delegate;

    @BeforeEach
    void setUp() {
        runner = new HookRunner();
        store = new AdjustedParamsStore();
        delegate = new RecordingTool();
    }

    @AfterEach
    void tearDown() {
        runner.close();
    }

    @Test
    @DisplayName("no hooks registered → delegate sees original params")
    void passThrough() {
        final Tool wrapped = BeforeToolCallHookWrapper.wrap(delegate, runner, store);
        final ToolResult result = wrapped.execute(
                new ToolRequest("echo", "call-1", Map.of("q", "hi")),
                ctx());
        assertThat(result.isSuccess()).isTrue();
        assertThat(delegate.lastParams).isEqualTo(Map.of("q", "hi"));
        assertThat(store.peek("call-1")).isEmpty();
    }

    @Test
    @DisplayName("hook rewrites params; delegate sees merged view; store records effective params")
    void paramRewrite() {
        final ModifyingHookHandler<BeforeToolCallEvent> handler = (evt, ctx) ->
                HookOutcome.modify(Map.of(BeforeToolCallHookWrapper.DELTA_PARAMS,
                        Map.of("q", "REWRITTEN", "added", 1)));
        runner.registerModifying(HookNames.BEFORE_TOOL_CALL, "p1", 0, handler);

        final Tool wrapped = BeforeToolCallHookWrapper.wrap(delegate, runner, store);
        wrapped.execute(new ToolRequest("echo", "call-2",
                Map.of("q", "hi", "keep", true)), ctx());

        assertThat(delegate.lastParams).containsEntry("q", "REWRITTEN");
        assertThat(delegate.lastParams).containsEntry("added", 1);
        assertThat(delegate.lastParams).containsEntry("keep", true);
        assertThat(store.consumeForToolCall("call-2"))
                .hasValueSatisfying(p -> assertThat(p).containsEntry("q", "REWRITTEN"));
    }

    @Test
    @DisplayName("hook flips block=true → TOOL_BLOCKED_BY_HOOK surfaces")
    void blocked() {
        final ModifyingHookHandler<BeforeToolCallEvent> handler = (evt, ctx) ->
                HookOutcome.modify(Map.of(
                        BeforeToolCallHookWrapper.DELTA_BLOCK, Boolean.TRUE,
                        BeforeToolCallHookWrapper.DELTA_BLOCK_REASON, "no traffic after hours"));
        runner.registerModifying(HookNames.BEFORE_TOOL_CALL, "gate", 0, handler);

        final Tool wrapped = BeforeToolCallHookWrapper.wrap(delegate, runner, store);
        assertThatThrownBy(() -> wrapped.execute(new ToolRequest("echo", "call-3", Map.of()), ctx()))
                .isInstanceOf(OpenClawException.class)
                .hasMessageContaining("no traffic after hours")
                .extracting("errorCode")
                .isEqualTo(ToolErrorCode.TOOL_BLOCKED_BY_HOOK);
        assertThat(delegate.lastParams).isNull(); // delegate never reached
    }

    @Test
    @DisplayName("wrap is idempotent")
    void wrapIdempotent() {
        final Tool once = BeforeToolCallHookWrapper.wrap(delegate, runner, store);
        final Tool twice = BeforeToolCallHookWrapper.wrap(once, runner, store);
        assertThat(twice).isSameAs(once);
        assertThat(BeforeToolCallHookWrapper.isAlreadyWrapped(once)).isTrue();
    }

    private ToolContext ctx() {
        return new ToolContext("s1", "agent", true, () -> false, new HashMap<>());
    }

    private static final class RecordingTool implements Tool {
        Map<String, Object> lastParams;

        @Override public String name() { return "echo"; }

        @Override public String description() { return "echo"; }

        @Override public Map<String, Object> parameters() { return Map.of(); }

        @Override
        public ToolResult execute(final ToolRequest request, final ToolContext context) {
            this.lastParams = request.params();
            return ToolResult.ok(request.params());
        }
    }
}
