package com.openclaw.plugin;

/**
 * SPI implemented by every openclaw plugin. Discovered at runtime via the
 * {@link java.util.ServiceLoader} mechanism — list the implementation FQN in
 * {@code META-INF/services/com.openclaw.plugin.OpenClawPlugin}.
 *
 * <p><b>Lifecycle</b>
 * <ol>
 *   <li>The runtime instantiates every declared plugin via its public no-arg
 *       constructor. {@link #id()} / {@link #version()} / {@link #description()}
 *       are probed <em>before</em> {@link #onLoad(PluginContext)} — they MUST
 *       return static values and never throw.</li>
 *   <li>{@link #onLoad(PluginContext)} runs once, after the core Spring
 *       {@code ApplicationContext} has finished refresh. The plugin can register
 *       beans, listeners, gateway methods, tools, etc. via the provided
 *       {@link PluginContext}.</li>
 *   <li>{@link #onUnload()} runs on graceful shutdown OR when a hot-reload
 *       removes the plugin. Implementations MUST release any file handles /
 *       threads they started in {@code onLoad}.</li>
 * </ol>
 *
 * <p><b>Threading</b>: {@code onLoad} / {@code onUnload} are invoked on the
 * main startup / shutdown thread — do not block for unbounded amounts of time.
 * Long-running workers should be scheduled onto the shared task executor that
 * the runtime exposes through {@link PluginContext}.
 *
 * <p><b>Ordering</b>: {@link #order()} controls relative load order between
 * plugins. Lower first. Default is {@code 0}; negative values let
 * infrastructure-ish plugins boot first.
 *
 * <p><b>Compatibility</b>: Plugin authors should depend on
 * {@code openclaw-plugin-sdk} with scope {@code provided} — the host runtime
 * will always supply a compatible version at boot time.
 */
public interface OpenClawPlugin {

    /** Stable identifier, e.g. {@code "hello"}, {@code "kube-diag"}. MUST be
     * unique across all loaded plugins, URL-safe, and free of spaces. */
    String id();

    /** Semver-style version string of this plugin release, e.g. {@code "0.1.0"}. */
    String version();

    /** Single-line human-readable summary for {@code list-plugins} endpoints. */
    String description();

    /** Relative load order. Lower boots first. Defaults to 0. */
    default int order() {
        return 0;
    }

    /** Called exactly once after the host context finishes refresh. Plugins
     * perform registration here. Throwing aborts plugin load — the runtime
     * logs and skips the plugin, it does NOT crash the application. */
    void onLoad(PluginContext context) throws Exception;

    /** Called on graceful shutdown or hot-unload. Default no-op. */
    default void onUnload() {
        // no-op
    }
}
