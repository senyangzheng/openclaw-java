package com.openclaw.plugins;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for the plugin loader. Prefix {@code openclaw.plugins} in
 * {@code application.yml}.
 *
 * <pre>
 * openclaw:
 *   plugins:
 *     enabled: true
 *     include: []          # explicit allow-list; empty ⇒ load every discovered plugin
 *     exclude: []          # deny-list overrides include
 *     fail-fast: false     # true ⇒ crash startup on first failing plugin
 * </pre>
 */
@ConfigurationProperties(prefix = "openclaw.plugins")
public class PluginProperties {

    /** Master switch. Default {@code true} — turning off skips discovery entirely. */
    private boolean enabled = true;

    /** Allow-list of plugin ids. Empty ⇒ no filtering. */
    private List<String> include = List.of();

    /** Deny-list of plugin ids. Wins over {@link #include}. */
    private List<String> exclude = List.of();

    /** When {@code true}, a single broken plugin aborts startup. Otherwise the
     * runtime logs a WARN and keeps going. Default {@code false}. */
    private boolean failFast = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getInclude() {
        return include;
    }

    public void setInclude(final List<String> include) {
        this.include = include == null ? List.of() : List.copyOf(include);
    }

    public List<String> getExclude() {
        return exclude;
    }

    public void setExclude(final List<String> exclude) {
        this.exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(final boolean failFast) {
        this.failFast = failFast;
    }
}
