package com.openclaw.plugins;

import java.util.List;
import java.util.Map;

import com.openclaw.plugin.CapabilityConflictException;
import com.openclaw.plugin.CapabilityType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CapabilityRegistry}. Covers:
 * <ul>
 *   <li>Hard-reject conflicts for GATEWAY_METHOD / HTTP_ROUTE / COMMAND / TOOL</li>
 *   <li>Allow-multiple behavior for HOOK</li>
 *   <li>Unregister semantics on plugin unload</li>
 *   <li>Defensive-copy snapshot isolation</li>
 *   <li>Blank-name / blank-pluginId validation</li>
 * </ul>
 */
class CapabilityRegistryTest {

    @Test
    void shouldRejectDuplicateGatewayMethodRegistrations() {
        final CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(CapabilityType.GATEWAY_METHOD, "chat.send", "plugin-a", new Object());

        assertThatThrownBy(() -> registry.register(
                CapabilityType.GATEWAY_METHOD, "chat.send", "plugin-b", new Object()))
                .isInstanceOf(CapabilityConflictException.class)
                .satisfies(ex -> {
                    final CapabilityConflictException cex = (CapabilityConflictException) ex;
                    assertThat(cex.existingPluginId()).isEqualTo("plugin-a");
                    assertThat(cex.pluginId()).isEqualTo("plugin-b");
                    assertThat(cex.name()).isEqualTo("chat.send");
                });
    }

    @Test
    void shouldRejectDuplicateHttpRouteAndCommandAndTool() {
        final CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(CapabilityType.HTTP_ROUTE, "GET /ping", "p1", new Object());
        registry.register(CapabilityType.COMMAND, "skills.list", "p1", new Object());
        registry.register(CapabilityType.TOOL, "clock.now", "p1", new Object());

        assertThatThrownBy(() -> registry.register(
                CapabilityType.HTTP_ROUTE, "GET /ping", "p2", new Object()))
                .isInstanceOf(CapabilityConflictException.class);
        assertThatThrownBy(() -> registry.register(
                CapabilityType.COMMAND, "skills.list", "p2", new Object()))
                .isInstanceOf(CapabilityConflictException.class);
        assertThatThrownBy(() -> registry.register(
                CapabilityType.TOOL, "clock.now", "p2", new Object()))
                .isInstanceOf(CapabilityConflictException.class);
    }

    @Test
    void shouldAllowMultipleHookRegistrationsFromDifferentPlugins() {
        final CapabilityRegistry registry = new CapabilityRegistry();
        final Object first = new Object();
        final Object second = new Object();
        registry.register(CapabilityType.HOOK, "before_agent_start", "plugin-a", first);
        registry.register(CapabilityType.HOOK, "before_agent_start", "plugin-b", second);

        final Map<String, List<CapabilityRegistry.Entry>> snap = registry.snapshot(CapabilityType.HOOK);
        assertThat(snap.get("before_agent_start")).hasSize(2);
        assertThat(snap.get("before_agent_start").get(0).pluginId()).isEqualTo("plugin-a");
        assertThat(snap.get("before_agent_start").get(1).pluginId()).isEqualTo("plugin-b");
    }

    @Test
    void shouldUnregisterAllCapabilitiesForAPlugin() {
        final CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(CapabilityType.TOOL, "a", "plugin-a", new Object());
        registry.register(CapabilityType.TOOL, "b", "plugin-a", new Object());
        registry.register(CapabilityType.TOOL, "c", "plugin-b", new Object());

        registry.unregisterPlugin("plugin-a");

        assertThat(registry.snapshot(CapabilityType.TOOL)).containsOnlyKeys("c");
        // After unload, the name slot is free — plugin-c can now claim "a" without conflict.
        registry.register(CapabilityType.TOOL, "a", "plugin-c", new Object());
        assertThat(registry.snapshot(CapabilityType.TOOL)).containsOnlyKeys("a", "c");
    }

    @Test
    void shouldReturnDefensiveCopiesFromSnapshot() {
        final CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(CapabilityType.TOOL, "x", "p", new Object());

        final Map<String, List<CapabilityRegistry.Entry>> snap1 = registry.snapshot(CapabilityType.TOOL);
        assertThatThrownBy(() -> snap1.put("y", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);

        registry.register(CapabilityType.TOOL, "y", "p", new Object());
        // snap1 is a snapshot → unchanged
        assertThat(snap1).containsOnlyKeys("x");
        // a fresh snapshot sees the new entry
        assertThat(registry.snapshot(CapabilityType.TOOL)).containsOnlyKeys("x", "y");
    }

    @Test
    void shouldReturnEmptyWhenCapabilityNotFound() {
        final CapabilityRegistry registry = new CapabilityRegistry();
        assertThat(registry.find(CapabilityType.TOOL, "missing")).isEmpty();
        assertThat(registry.snapshot(CapabilityType.TOOL)).isEmpty();
    }

    @Test
    void shouldRejectBlankNameOrPluginId() {
        final CapabilityRegistry registry = new CapabilityRegistry();
        assertThatIllegalArgumentException().isThrownBy(() ->
                registry.register(CapabilityType.TOOL, "", "p", new Object()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                registry.register(CapabilityType.TOOL, "n", "", new Object()));
    }
}
