package com.openclaw.config.hotreload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * File-system watcher that drives {@link ConfigReloadPublisher} whenever one of
 * the configured files is modified / created / deleted.
 *
 * <h2>How it watches</h2>
 * {@link java.nio.file.WatchService} only operates at the directory level, so we
 * collect the distinct parent directories of every configured path and register
 * a single key per directory. Events outside the watch set are filtered out by
 * comparing the resolved file name.
 *
 * <h2>Debounce</h2>
 * A typical "save file in editor" generates 2-3 MODIFY events within a few ms.
 * We collapse these by stamping {@code lastEventNanoByFile}: when an event
 * arrives, we update the stamp, wait the debounce window, then publish if and
 * only if no newer event has arrived meanwhile. Done with the watcher thread
 * itself using {@code WatchService#poll(timeout)} — no extra scheduler needed.
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #afterPropertiesSet()} opens the {@link WatchService}, registers
 *       directories, and spins up a single daemon thread.</li>
 *   <li>{@link #destroy()} closes the service, which unblocks the thread's
 *       {@code poll} call and lets it terminate cleanly.</li>
 * </ul>
 */
public class ConfigWatcher implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConfigWatcher.class);

    /** Thread name — single daemon. Easy to spot in jstack output. */
    private static final String THREAD_NAME = "openclaw-config-watcher";

    private final HotReloadProperties properties;
    private final ConfigReloadPublisher publisher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Nanotime of the latest raw event per watched file. Used for debounce. */
    private final Map<Path, Long> lastEventNanoByFile = new ConcurrentHashMap<>();

    private WatchService watchService;
    private Thread watcherThread;

    /** Absolute file paths we were asked to watch. */
    private Set<Path> watchedFiles = Set.of();

    public ConfigWatcher(final HotReloadProperties properties,
                          final ConfigReloadPublisher publisher) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        if (!properties.isEnabled()) {
            log.info("config.hotreload.disabled (openclaw.config.hot-reload.enabled=false)");
            return;
        }
        if (properties.getPaths().isEmpty()) {
            log.warn("config.hotreload.enabled but paths=[]; watcher will not start");
            return;
        }
        this.watchedFiles = resolvePaths(properties.getPaths());
        if (watchedFiles.isEmpty()) {
            log.warn("config.hotreload no resolvable paths; watcher will not start");
            return;
        }
        this.watchService = FileSystems.getDefault().newWatchService();
        registerParentDirs(watchedFiles);

        running.set(true);
        watcherThread = new Thread(this::watchLoop, THREAD_NAME);
        watcherThread.setDaemon(true);
        watcherThread.start();
        log.info("config.hotreload.started files={} debounce={}ms",
            watchedFiles, properties.getDebounce().toMillis());
    }

    private Set<Path> resolvePaths(final List<String> raw) {
        final Set<Path> paths = new HashSet<>();
        for (String p : raw) {
            try {
                paths.add(Paths.get(p).toAbsolutePath().normalize());
            } catch (Exception ex) {
                log.warn("config.hotreload.path.invalid path={} cause={}", p, ex.toString());
            }
        }
        return Set.copyOf(paths);
    }

    private void registerParentDirs(final Set<Path> files) throws IOException {
        final Set<Path> parents = new HashSet<>();
        for (Path f : files) {
            final Path dir = f.getParent();
            if (dir != null && parents.add(dir)) {
                if (!Files.isDirectory(dir)) {
                    log.warn("config.hotreload.parent.missing dir={}", dir);
                    continue;
                }
                dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            }
        }
    }

    /** Main loop. Uses {@code poll(debounce)} rather than {@code take()} so a
     * lone event (no follow-up within the debounce window) still gets drained —
     * otherwise the thread would block in {@code take()} forever after a single
     * MODIFY and miss the publish. Exits when {@link #running} flips to false
     * AND the watch service is closed. */
    private void watchLoop() {
        final long debounceNanos = properties.getDebounce().toNanos();
        while (running.get()) {
            try {
                final WatchKey key = watchService.poll(debounceNanos, TimeUnit.NANOSECONDS);
                if (key != null) {
                    handleKey(key);
                    key.reset();
                }
                drainDebounce();
            } catch (ClosedWatchServiceException | InterruptedException ex) {
                log.debug("config.hotreload.loop.exit reason={}", ex.toString());
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("config.hotreload.loop.error cause={}", ex.toString());
            }
        }
    }

    private void handleKey(final WatchKey key) {
        final Path watchDir = (Path) key.watchable();
        for (WatchEvent<?> ev : key.pollEvents()) {
            if (ev.kind() == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }
            final Path name = (Path) ev.context();
            if (name == null) {
                continue;
            }
            final Path absolute = watchDir.resolve(name).toAbsolutePath().normalize();
            if (!watchedFiles.contains(absolute)) {
                continue;
            }
            lastEventNanoByFile.put(absolute, System.nanoTime());
            log.debug("config.hotreload.event path={} kind={}", absolute, ev.kind().name());
        }
    }

    /** Walk the {@code lastEventNanoByFile} map and publish any entry whose
     * latest event is older than {@link HotReloadProperties#getDebounce()}. */
    private void drainDebounce() {
        final long now = System.nanoTime();
        final long debounceNanos = properties.getDebounce().toNanos();
        final List<Map.Entry<Path, Long>> snapshot = List.copyOf(
            new HashMap<>(lastEventNanoByFile).entrySet());
        for (Map.Entry<Path, Long> e : snapshot) {
            if (now - e.getValue() >= debounceNanos) {
                lastEventNanoByFile.remove(e.getKey(), e.getValue());
                final String kind = Files.exists(e.getKey()) ? "modify" : "delete";
                publisher.publish(new ConfigChangeEvent(e.getKey(), kind));
            }
        }
    }

    @Override
    public void destroy() {
        running.set(false);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ex) {
                log.warn("config.hotreload.close.failed cause={}", ex.toString());
            }
        }
        if (watcherThread != null) {
            try {
                watcherThread.join(Duration.ofSeconds(2).toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
