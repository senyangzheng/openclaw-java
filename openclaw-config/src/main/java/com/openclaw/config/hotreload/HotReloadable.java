package com.openclaw.config.hotreload;

/**
 * SPI for components that can react to runtime configuration changes without a
 * full context restart. Implement this on any Spring bean and it will be
 * invoked whenever a watched config file changes.
 *
 * <p><b>Threading</b>: the callback runs on the watcher's single daemon thread.
 * Don't perform long blocking work there — delegate to a task executor.
 *
 * <p><b>Idempotency</b>: debounce already collapses burst events, but implementers
 * MUST still be safe to call repeatedly with no net effect (e.g. re-reading the
 * same file twice should not duplicate log lines or state).
 *
 * <p><b>Error handling</b>: exceptions thrown from {@link #onConfigReloaded} are
 * caught by the watcher and logged at WARN — they never abort the reload loop.
 * Handlers should therefore convert their own failures into meaningful log
 * messages rather than relying on the watcher to surface them.
 */
public interface HotReloadable {

    /** React to a debounced config file change. */
    void onConfigReloaded(ConfigChangeEvent event);
}
