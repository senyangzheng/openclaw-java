package com.openclaw.config.hotreload;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Fired by the watcher when one of the configured files has changed and the
 * debounce window has elapsed. Delivered:
 * <ol>
 *   <li>Synchronously to every {@link HotReloadable} bean — so handlers run
 *       on the watcher thread, NOT the request thread;</li>
 *   <li>Then as a Spring {@code ApplicationEvent} through
 *       {@link org.springframework.context.ApplicationEventPublisher} for loosely
 *       coupled listeners.</li>
 * </ol>
 *
 * @param path     absolute path of the file whose modification triggered the event
 * @param kind     short token: {@code "create" | "modify" | "delete"}
 * @param occurred wall-clock moment the reload callback started
 */
public record ConfigChangeEvent(Path path, String kind, Instant occurred) {

    public ConfigChangeEvent(final Path path, final String kind) {
        this(path, kind, Instant.now());
    }
}
