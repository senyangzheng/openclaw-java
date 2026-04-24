package com.openclaw.agents.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.openclaw.stream.AgentEvent;
import com.openclaw.stream.AgentEventSink;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Event aggregation layer for a single agent run.
 *
 * <p>Mirrors openclaw-ts {@code src/agents/pi-embedded-runner/subscribe.ts} — each run owns exactly one
 * {@code SubscribeState} that multiplexes emitted {@link AgentEvent}s to multiple subscribers (HTTP SSE
 * client, WebSocket peers, auditing sink, ...).
 *
 * <p><b>Lifecycle</b>:
 * <ol>
 *   <li>Created with the run (state {@link AgentRunState#IDLE})</li>
 *   <li>{@link #emit(AgentEvent)} is called by the attempt; every subscriber gets a copy via a
 *       {@link Sinks.Many#unicast()} per subscriber ({@code Flux#publish} multicast is avoided because it
 *       drops messages to late subscribers — unacceptable for completion audit trails)</li>
 *   <li>{@link #complete()} or {@link #error(Throwable)} terminates every downstream flux</li>
 *   <li>Internal {@link AgentEventSink} accumulates the full stream for post-run persistence (assistantText,
 *       toolCalls, finishReason, usage)</li>
 * </ol>
 *
 * <p>Not a general-purpose event bus — scoped to one agent run. Thread-safe.
 */
public final class SubscribeState {

    private final Sinks.Many<AgentEvent> broadcast;
    private final AgentEventSink accumulator;
    private final AtomicBoolean terminated;
    private final List<Consumer<AgentEvent>> peekers;

    public SubscribeState() {
        this.broadcast = Sinks.many().multicast().onBackpressureBuffer();
        this.accumulator = new AgentEventSink();
        this.terminated = new AtomicBoolean(false);
        this.peekers = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Emit an event to all subscribers and fold it into the internal sink.
     * Calling {@code emit} after {@link #complete()}/{@link #error(Throwable)} is a no-op.
     */
    public void emit(final AgentEvent event) {
        Objects.requireNonNull(event, "event");
        if (terminated.get()) {
            return;
        }
        accumulator.accept(event);
        // Synchronously notify peekers first (they see events before the flux).
        final List<Consumer<AgentEvent>> snapshot;
        synchronized (peekers) {
            snapshot = new ArrayList<>(peekers);
        }
        for (final Consumer<AgentEvent> peeker : snapshot) {
            try {
                peeker.accept(event);
            } catch (RuntimeException ignored) {
                // peekers are observational — never break the stream due to their faults
            }
        }
        broadcast.tryEmitNext(event);
    }

    /** Subscribe to the event stream. Multiple subscribers receive the same events. */
    public Flux<AgentEvent> subscribe() {
        return broadcast.asFlux();
    }

    /**
     * Register a side-effect peeker invoked synchronously from {@link #emit(AgentEvent)} (before the flux).
     * Use for low-latency tracing / metric fan-out; long-running work should subscribe via {@link #subscribe()}.
     */
    public void peek(final Consumer<AgentEvent> peeker) {
        peekers.add(Objects.requireNonNull(peeker, "peeker"));
    }

    /** Complete the stream. Idempotent. */
    public void complete() {
        if (terminated.compareAndSet(false, true)) {
            broadcast.tryEmitComplete();
        }
    }

    /** Terminate with error. Idempotent. */
    public void error(final Throwable err) {
        if (terminated.compareAndSet(false, true)) {
            broadcast.tryEmitError(err == null ? new IllegalStateException("unknown") : err);
        }
    }

    public boolean isTerminated() {
        return terminated.get();
    }

    public AgentEventSink.Snapshot snapshot() {
        return accumulator.snapshot();
    }

    public AgentEventSink accumulator() {
        return accumulator;
    }
}
