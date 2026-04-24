package com.openclaw.tools.runtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.tools.Tool;
import com.openclaw.tools.ToolErrorCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Named-capability {@link Tool} registry. Duplicate names are a hard rejection per plan §08 plugin
 * governance — tool methods are route-like resources and collisions must fail loud rather than silently
 * override.
 *
 * <p>This is the M3.1 skeleton: lookup by name, conflict rejection, snapshot of registered tools. The full
 * M3.2 registry additionally layers scoped allow-lists (per-profile / per-agent / sandbox / subagent) on
 * top; those live on {@link com.openclaw.tools.runtime.policy.ToolPolicyPipeline} rather than here so the
 * raw registry stays a pure catalog.
 */
public final class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> byName = new ConcurrentHashMap<>();

    /**
     * Register a tool. Throws {@link OpenClawException} with {@link ToolErrorCode#TOOL_NAME_CONFLICT} when
     * another tool is already bound to the same name.
     */
    public void register(final Tool tool) {
        Objects.requireNonNull(tool, "tool");
        final String name = Objects.requireNonNull(tool.name(), "tool.name");
        final Tool previous = byName.putIfAbsent(name, tool);
        if (previous != null && previous != tool) {
            throw new OpenClawException(
                    ToolErrorCode.TOOL_NAME_CONFLICT,
                    "duplicate tool name: " + name + " (existing=" + previous.getClass().getName()
                            + ", new=" + tool.getClass().getName() + ")");
        }
        log.info("tools.registry.registered name={} impl={}", name, tool.getClass().getName());
    }

    /** Register many tools atomically: fail the whole batch on the first conflict. */
    public void registerAll(final Collection<? extends Tool> tools) {
        Objects.requireNonNull(tools, "tools");
        final Map<String, Tool> pending = new LinkedHashMap<>();
        for (final Tool tool : tools) {
            if (pending.putIfAbsent(Objects.requireNonNull(tool.name()), tool) != null) {
                throw new OpenClawException(ToolErrorCode.TOOL_NAME_CONFLICT,
                        "duplicate tool name in batch: " + tool.name());
            }
        }
        pending.forEach((name, tool) -> register(tool));
    }

    /** Unregister by name; idempotent (returns {@code true} when a tool was actually removed). */
    public boolean unregister(final String name) {
        if (name == null) {
            return false;
        }
        return byName.remove(name) != null;
    }

    public Optional<Tool> find(final String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Snapshot of all registered tools, in no particular order. */
    public List<Tool> all() {
        return List.copyOf(byName.values());
    }

    public int size() {
        return byName.size();
    }
}
