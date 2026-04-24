package com.openclaw.plugin;

/**
 * Kinds of named capabilities a plugin can register through {@link PluginContext}. Used as the namespace key
 * for conflict detection — two plugins registering the same {@code (type, name)} triggers the governance
 * rule defined per-type below.
 *
 * <p>Mirrors the TS {@code PluginRegistry} capability groups; we exclude {@code SCHEDULED_JOB} / {@code MEMORY_SLOT}
 * for M3 preparation (they land in M5 along with ElasticJob / multi-plugin memory routing).
 */
public enum CapabilityType {

    /**
     * Gateway JSON-RPC method name, e.g. {@code chat.send}. Conflicts <b>hard-reject</b>: two plugins exposing
     * the same method name is always a bug (the dispatcher wouldn't know which to pick).
     */
    GATEWAY_METHOD(ConflictPolicy.HARD_REJECT),

    /**
     * HTTP route key, e.g. {@code "POST /plugin/hello"}. Conflicts <b>hard-reject</b> — Spring MVC already
     * refuses duplicate mappings, we catch it earlier with a richer diagnostic.
     */
    HTTP_ROUTE(ConflictPolicy.HARD_REJECT),

    /**
     * CLI command name, e.g. {@code skills.list}. Conflicts <b>hard-reject</b> — picocli would otherwise
     * silently drop the duplicate.
     */
    COMMAND(ConflictPolicy.HARD_REJECT),

    /**
     * Agent tool name, e.g. {@code clock.now}. Conflicts <b>hard-reject</b> — tool resolution is by name, a
     * duplicate would create non-deterministic execution.
     */
    TOOL(ConflictPolicy.HARD_REJECT),

    /**
     * Hook registration (by hook name, e.g. {@code before_agent_start}). Conflicts are <b>allowed</b>:
     * multiple plugins can register the same hook point — the {@link HookRunner} orders them by priority.
     */
    HOOK(ConflictPolicy.ALLOW_MULTIPLE);

    private final ConflictPolicy policy;

    CapabilityType(final ConflictPolicy policy) {
        this.policy = policy;
    }

    public ConflictPolicy policy() {
        return policy;
    }

    /** How this capability type treats two plugins trying to claim the same name. */
    public enum ConflictPolicy {
        /** Any conflict hard-rejects; the second registration fails. */
        HARD_REJECT,
        /** Multiple registrations are allowed (e.g. hook chains). */
        ALLOW_MULTIPLE
    }
}
