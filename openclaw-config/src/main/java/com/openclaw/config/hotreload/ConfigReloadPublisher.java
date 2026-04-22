package com.openclaw.config.hotreload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Objects;

/**
 * Fans out a {@link ConfigChangeEvent} to every {@link HotReloadable} bean, then
 * rebroadcasts via Spring's {@link ApplicationEventPublisher} so listener-style
 * consumers (e.g. gateway reload endpoints) can plug in too.
 *
 * <p>Kept as its own class so {@link ConfigWatcher} stays focused on I/O and
 * debounce — this layer only cares about dispatch.
 */
public final class ConfigReloadPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfigReloadPublisher.class);

    private final List<HotReloadable> listeners;
    private final ApplicationEventPublisher eventPublisher;

    public ConfigReloadPublisher(final List<HotReloadable> listeners,
                                  final ApplicationEventPublisher eventPublisher) {
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners"));
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    /** Best-effort fan-out. A failing listener never blocks the others. */
    public void publish(final ConfigChangeEvent event) {
        Objects.requireNonNull(event, "event");
        log.info("config.reload path={} kind={} listeners={}",
            event.path(), event.kind(), listeners.size());
        for (HotReloadable listener : listeners) {
            try {
                listener.onConfigReloaded(event);
            } catch (Exception ex) {
                log.warn("config.reload.listener.failed listener={} cause={}",
                    listener.getClass().getName(), ex.toString());
            }
        }
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("config.reload.eventPublisher.failed cause={}", ex.toString());
        }
    }
}
