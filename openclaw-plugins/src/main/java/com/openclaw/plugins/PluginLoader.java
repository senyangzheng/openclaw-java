package com.openclaw.plugins;

import com.openclaw.plugin.OpenClawPlugin;
import com.openclaw.plugin.PluginDescriptor;
import com.openclaw.plugin.PluginLoadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Discovers {@link OpenClawPlugin} instances via {@link ServiceLoader} and
 * drives their lifecycle.
 *
 * <h2>Discovery order</h2>
 * Plugins declared in any classpath jar under
 * {@code META-INF/services/com.openclaw.plugin.OpenClawPlugin} are instantiated
 * via public no-arg constructor, filtered by {@link PluginProperties} include /
 * exclude, sorted by {@link OpenClawPlugin#order()} (ascending), then passed to
 * {@link OpenClawPlugin#onLoad(com.openclaw.plugin.PluginContext)} one at a time.
 *
 * <h2>Error handling</h2>
 * A broken plugin (thrown from {@code onLoad}) is logged with its id and skipped
 * — unless {@link PluginProperties#isFailFast()} is on, in which case we rethrow
 * and abort startup. This matches the TypeScript openclaw behaviour where a
 * single broken plugin never brings down the whole runtime.
 *
 * <h2>Shutdown</h2>
 * On {@link ContextClosedEvent} every loaded plugin gets
 * {@link OpenClawPlugin#onUnload()} called in reverse load order. Exceptions from
 * {@code onUnload} are logged but otherwise swallowed — shutdown is best-effort.
 */
public class PluginLoader implements
        ApplicationListener<ContextRefreshedEvent>, PluginRegistry, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

    private final ConfigurableApplicationContext applicationContext;
    private final PluginProperties properties;
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    /** ordered: iteration = load order */
    private final Map<String, LoadedPlugin> loadedPlugins = Collections.synchronizedMap(new LinkedHashMap<>());

    public PluginLoader(final ConfigurableApplicationContext applicationContext,
                        final PluginProperties properties) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        if (!loaded.compareAndSet(false, true)) {
            return;
        }
        if (!properties.isEnabled()) {
            log.info("plugins.disabled (openclaw.plugins.enabled=false)");
            return;
        }
        final List<OpenClawPlugin> discovered = discover();
        if (discovered.isEmpty()) {
            log.info("plugins.discovered count=0");
            return;
        }
        log.info("plugins.discovered count={} ids={}", discovered.size(),
            discovered.stream().map(OpenClawPlugin::id).toList());

        for (OpenClawPlugin plugin : discovered) {
            try {
                loadOne(plugin);
            } catch (RuntimeException ex) {
                log.warn("plugins.load.failed id={} cause={}", plugin.id(), ex.toString());
                if (properties.isFailFast()) {
                    throw ex;
                }
            }
        }
    }

    /** Instantiate every declared plugin, apply include/exclude + order filters. */
    private List<OpenClawPlugin> discover() {
        final List<OpenClawPlugin> all = new ArrayList<>();
        final Set<String> seenIds = new HashSet<>();
        final ServiceLoader<OpenClawPlugin> serviceLoader =
            ServiceLoader.load(OpenClawPlugin.class, Thread.currentThread().getContextClassLoader());
        for (OpenClawPlugin plugin : serviceLoader) {
            final String id = plugin.id();
            if (!StringUtils.hasText(id)) {
                log.warn("plugins.discover.skip reason=blank-id class={}",
                    plugin.getClass().getName());
                continue;
            }
            if (!seenIds.add(id)) {
                log.warn("plugins.discover.skip reason=duplicate-id id={} class={}",
                    id, plugin.getClass().getName());
                continue;
            }
            if (!properties.getInclude().isEmpty() && !properties.getInclude().contains(id)) {
                log.info("plugins.discover.filtered id={} reason=not-in-include", id);
                continue;
            }
            if (properties.getExclude().contains(id)) {
                log.info("plugins.discover.filtered id={} reason=in-exclude", id);
                continue;
            }
            all.add(plugin);
        }
        all.sort(Comparator.comparingInt(OpenClawPlugin::order).thenComparing(OpenClawPlugin::id));
        return all;
    }

    private void loadOne(final OpenClawPlugin plugin) {
        final String id = plugin.id();
        final DefaultPluginContext ctx = new DefaultPluginContext(id, applicationContext);
        try {
            plugin.onLoad(ctx);
        } catch (Exception ex) {
            throw new PluginLoadException(id, "plugin.onLoad failed: " + ex.getMessage(), ex);
        }
        final PluginDescriptor descriptor = new PluginDescriptor(
            id,
            plugin.version(),
            plugin.description(),
            plugin.order(),
            plugin.getClass().getName(),
            Instant.now());
        loadedPlugins.put(id, new LoadedPlugin(plugin, descriptor));
        log.info("plugins.loaded id={} version={} class={}",
            id, plugin.version(), plugin.getClass().getName());
    }

    @Override
    public List<PluginDescriptor> descriptors() {
        synchronized (loadedPlugins) {
            return loadedPlugins.values().stream().map(LoadedPlugin::descriptor).toList();
        }
    }

    @Override
    public Optional<PluginDescriptor> find(final String id) {
        return Optional.ofNullable(loadedPlugins.get(id)).map(LoadedPlugin::descriptor);
    }

    @Override
    public Optional<OpenClawPlugin> plugin(final String id) {
        return Optional.ofNullable(loadedPlugins.get(id)).map(LoadedPlugin::plugin);
    }

    @Override
    public void destroy() {
        unloadAll();
    }

    /** Invoke {@code onUnload} in reverse load order. Best-effort. */
    private void unloadAll() {
        final List<LoadedPlugin> ordered;
        synchronized (loadedPlugins) {
            ordered = new ArrayList<>(loadedPlugins.values());
            loadedPlugins.clear();
        }
        Collections.reverse(ordered);
        for (LoadedPlugin lp : ordered) {
            try {
                lp.plugin().onUnload();
                log.info("plugins.unloaded id={}", lp.descriptor().id());
            } catch (Exception ex) {
                log.warn("plugins.unload.failed id={} cause={}",
                    lp.descriptor().id(), ex.toString());
            }
        }
    }

    /** Tuple holding the live plugin instance and its snapshot descriptor. */
    private record LoadedPlugin(OpenClawPlugin plugin, PluginDescriptor descriptor) {
    }
}
