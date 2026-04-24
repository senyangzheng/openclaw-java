package com.openclaw.plugin;

/**
 * Origin of a plugin. Lower-priority sources can be overridden by higher-priority ones when the same plugin
 * id appears in multiple places — mirrors OpenClaw-TS plugin source semantics in
 * {@code src/infra/plugins-loader.ts}.
 *
 * <p>Priority (highest last, so {@code ordinal()} corresponds to strength):
 * <ol>
 *   <li>{@link #BUNDLED} — shipped with the openclaw-java runtime jar (lowest)</li>
 *   <li>{@link #GLOBAL}  — installed into the user's openclaw home ({@code ~/.openclaw/plugins})</li>
 *   <li>{@link #WORKSPACE} — workspace-local plugins (e.g. {@code .openclaw/plugins} under the repo)</li>
 *   <li>{@link #CONFIG}  — explicitly declared in {@code application.yml}
 *       ({@code openclaw.plugins.sources[]}), highest priority</li>
 * </ol>
 *
 * <p>Higher priority wins when two sources register a plugin with the same id; the loser is dropped and a
 * diagnostic entry is emitted so operators can see the override in {@code /actuator/plugins}.
 *
 * <p>M3 preparation: the runtime currently only produces {@link #BUNDLED} sources (classpath
 * {@code META-INF/services}); the GLOBAL / WORKSPACE / CONFIG loaders arrive in M5. The enum is introduced
 * now so capability registry semantics are stable from day one.
 */
public enum PluginSource {

    BUNDLED(0, "bundled"),
    GLOBAL(1, "global"),
    WORKSPACE(2, "workspace"),
    CONFIG(3, "config");

    private final int priority;
    private final String displayName;

    PluginSource(final int priority, final String displayName) {
        this.priority = priority;
        this.displayName = displayName;
    }

    /** Higher number = higher priority. Stable and ordered. */
    public int priority() {
        return priority;
    }

    public String displayName() {
        return displayName;
    }

    /** {@code true} when {@code this} should override {@code other} in a conflict. */
    public boolean overrides(final PluginSource other) {
        return other != null && this.priority > other.priority;
    }
}
