package com.openclaw.tools.runtime;

import java.util.List;
import java.util.Map;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.tools.Tool;
import com.openclaw.tools.ToolContext;
import com.openclaw.tools.ToolErrorCode;
import com.openclaw.tools.ToolRequest;
import com.openclaw.tools.ToolResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    private final ToolRegistry registry = new ToolRegistry();

    @Test
    @DisplayName("register + find round-trips a single tool")
    void register_findRoundTrip() {
        final Tool tool = new StubTool("echo");
        registry.register(tool);
        assertThat(registry.find("echo")).hasValue(tool);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("duplicate name rejected with TOOL_NAME_CONFLICT")
    void duplicateNameRejected() {
        registry.register(new StubTool("echo"));
        assertThatThrownBy(() -> registry.register(new StubTool("echo")))
                .isInstanceOf(OpenClawException.class)
                .hasMessageContaining("duplicate tool name")
                .extracting("errorCode")
                .isEqualTo(ToolErrorCode.TOOL_NAME_CONFLICT);
    }

    @Test
    @DisplayName("registerAll is atomic — conflict in batch rolls back nothing (fails fast)")
    void registerAllAtomic() {
        final List<Tool> batch = List.of(new StubTool("a"), new StubTool("b"), new StubTool("a"));
        assertThatThrownBy(() -> registry.registerAll(batch))
                .isInstanceOf(OpenClawException.class);
        assertThat(registry.size()).isZero();
    }

    @Test
    @DisplayName("unregister removes the binding and is idempotent")
    void unregister() {
        registry.register(new StubTool("echo"));
        assertThat(registry.unregister("echo")).isTrue();
        assertThat(registry.unregister("echo")).isFalse();
        assertThat(registry.find("echo")).isEmpty();
    }

    private static final class StubTool implements Tool {
        private final String name;

        StubTool(final String name) {
            this.name = name;
        }

        @Override public String name() { return name; }

        @Override public String description() { return "stub"; }

        @Override public Map<String, Object> parameters() { return Map.of(); }

        @Override
        public ToolResult execute(final ToolRequest request, final ToolContext context) {
            return ToolResult.ok(request.params());
        }
    }
}
