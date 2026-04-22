package com.openclaw.config.hotreload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the full file-watch loop. Uses {@link TempDir} for a sandboxed
 * directory + a stub listener whose callback count is asserted via Awaitility.
 *
 * <p>We don't mock the {@link java.nio.file.WatchService}: the whole value of
 * this test is proving that real FS events (triggered by
 * {@link Files#writeString}) are observed, debounced, and dispatched on the
 * watcher's own thread. macOS polling backend can be slow — we allow 5 seconds
 * for the first reload callback.
 */
class ConfigWatcherTest {

    /** Very short debounce so the test doesn't have to wait the 500ms default. */
    private static final Duration DEBOUNCE = Duration.ofMillis(100);

    /** Generous upper bound for FS event propagation. macOS uses a 2s-ish poll
     * interval on some JDKs, so we budget 8s before declaring failure. */
    private static final Duration EVENT_BUDGET = Duration.ofSeconds(8);

    private ConfigWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.destroy();
        }
    }

    @Test
    void shouldNotifyHotReloadableOnFileModify(@TempDir final Path tmp) throws IOException, InterruptedException {
        final Path target = tmp.resolve("openclaw-overrides.yml");
        Files.writeString(target, "initial: 1\n", StandardCharsets.UTF_8);

        final RecordingListener listener = new RecordingListener();
        final HotReloadProperties properties = new HotReloadProperties();
        properties.setEnabled(true);
        properties.setPaths(List.of(target.toString()));
        properties.setDebounce(DEBOUNCE);

        watcher = new ConfigWatcher(properties,
            new ConfigReloadPublisher(List.of(listener), event -> { /* no-op */ }));
        watcher.afterPropertiesSet();

        // Give the watcher a moment to register the parent dir before we mutate.
        Thread.sleep(200);

        Files.writeString(target, "initial: 2\n", StandardCharsets.UTF_8);

        assertThat(waitFor(() -> !listener.events.isEmpty(), EVENT_BUDGET))
            .as("expected at least one reload event for %s", target)
            .isTrue();

        assertThat(listener.events.get(0).path()).isEqualTo(target.toAbsolutePath().normalize());
        assertThat(listener.events.get(0).kind()).isIn("modify", "create");
    }

    @Test
    void shouldRepublishAlsoToApplicationEventPublisher(@TempDir final Path tmp) throws IOException, InterruptedException {
        final Path target = tmp.resolve("another.yml");
        Files.writeString(target, "a\n", StandardCharsets.UTF_8);

        final RecordingPublisher pub = new RecordingPublisher();
        final HotReloadProperties properties = new HotReloadProperties();
        properties.setEnabled(true);
        properties.setPaths(List.of(target.toString()));
        properties.setDebounce(DEBOUNCE);

        watcher = new ConfigWatcher(properties, new ConfigReloadPublisher(List.of(), pub));
        watcher.afterPropertiesSet();
        Thread.sleep(200);

        Files.writeString(target, "b\n", StandardCharsets.UTF_8);

        assertThat(waitFor(() -> !pub.received.isEmpty(), EVENT_BUDGET))
            .as("ApplicationEventPublisher should receive ConfigChangeEvent")
            .isTrue();
        assertThat(pub.received.get(0)).isInstanceOf(ConfigChangeEvent.class);
    }

    @Test
    void shouldStayQuietWhenDisabled(@TempDir final Path tmp) throws IOException {
        final Path target = tmp.resolve("no-watch.yml");
        Files.writeString(target, "x\n", StandardCharsets.UTF_8);

        final RecordingListener listener = new RecordingListener();
        final HotReloadProperties properties = new HotReloadProperties();
        properties.setEnabled(false);
        properties.setPaths(List.of(target.toString()));

        watcher = new ConfigWatcher(properties,
            new ConfigReloadPublisher(List.of(listener), event -> { /* no-op */ }));
        watcher.afterPropertiesSet();

        // No watcher thread should be running → sleep briefly and assert nothing fired.
        try {
            TimeUnit.MILLISECONDS.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(listener.events).isEmpty();
    }

    /** Spin-wait until the supplier returns {@code true} or the budget elapses.
     * Poll interval is 50ms — plenty fast for a WatchService-scale test. */
    private static boolean waitFor(final BooleanSupplier supplier, final Duration budget) {
        final long deadline = System.nanoTime() + budget.toNanos();
        while (System.nanoTime() < deadline) {
            if (supplier.getAsBoolean()) {
                return true;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return supplier.getAsBoolean();
    }

    /** Collects every event for later assertion. Thread-safe. */
    private static final class RecordingListener implements HotReloadable {

        final List<ConfigChangeEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onConfigReloaded(final ConfigChangeEvent event) {
            events.add(event);
        }
    }

    /** Minimal stand-in for Spring's {@link ApplicationEventPublisher}. */
    private static final class RecordingPublisher implements ApplicationEventPublisher {

        final List<Object> received = new CopyOnWriteArrayList<>();

        @Override
        public void publishEvent(final Object event) {
            received.add(event);
        }
    }
}
