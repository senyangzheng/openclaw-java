package com.openclaw.config.hotreload;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the file-watch based hot-reload mechanism. Prefix
 * {@code openclaw.config.hot-reload} in {@code application.yml}.
 *
 * <pre>
 * openclaw:
 *   config:
 *     hot-reload:
 *       enabled: true
 *       paths:
 *         - /etc/openclaw/overrides.yml
 *       debounce: 500ms
 * </pre>
 *
 * <p>When {@link #isEnabled()} is true the runtime starts a daemon thread that
 * polls {@link java.nio.file.WatchService} events on every parent directory of
 * the listed paths. File-modify / create events inside those directories are
 * debounced and dispatched to every {@link HotReloadable} bean via
 * {@link ConfigReloadPublisher}.
 */
@ConfigurationProperties(prefix = "openclaw.config.hot-reload")
public class HotReloadProperties {

    /** Master switch. Default {@code false} — hot reload is opt-in. */
    private boolean enabled = false;

    /** Absolute paths of config files to watch. Non-existent files are allowed —
     * the watcher will pick them up on first create. */
    private List<String> paths = List.of();

    /** Collapse bursts of FS events (e.g. editor save = modify-then-modify)
     * within this window into a single reload callback. Default 500ms. */
    private Duration debounce = Duration.ofMillis(500);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(final List<String> paths) {
        this.paths = paths == null ? List.of() : List.copyOf(paths);
    }

    public Duration getDebounce() {
        return debounce;
    }

    public void setDebounce(final Duration debounce) {
        this.debounce = debounce == null ? Duration.ofMillis(500) : debounce;
    }
}
