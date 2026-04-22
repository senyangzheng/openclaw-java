package com.openclaw.autoreply.command;

import com.openclaw.channels.core.InboundMessage;

/**
 * Front-of-pipeline hook. Every registered {@code ChatCommand} sees every
 * inbound message before it reaches the LLM; the first command that returns
 * {@code true} from {@link #matches(InboundMessage)} gets to produce the reply,
 * bypassing {@link com.openclaw.providers.api.ProviderClient} entirely.
 *
 * <p><b>Use cases</b>
 * <ul>
 *   <li>Plugin-contributed chat shortcuts (e.g. the built-in "/hello" demo);</li>
 *   <li>Ops commands ("/reload", "/whoami") that don't cost provider tokens;</li>
 *   <li>Deterministic routing (e.g. an FAQ command that short-circuits the LLM
 *       for known questions).</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #matches(InboundMessage)} MUST be a pure function and MUST NOT
 *       throw — it's called on every message.</li>
 *   <li>{@link #handle(InboundMessage)} is only invoked when {@code matches}
 *       returned {@code true}; implementations MAY throw, in which case the
 *       pipeline falls back to the LLM path (see {@link ChatCommandDispatcher}).</li>
 *   <li>{@link #order()} breaks ties between commands that both match — lower
 *       runs first. Default {@code 0}.</li>
 *   <li>Implementations MUST be thread-safe; the dispatcher can be invoked
 *       concurrently from multiple Web / SSE / CLI threads.</li>
 * </ul>
 */
public interface ChatCommand {

    /** Short, URL-safe identifier used in logs and, later, in gateway method listing. */
    String name();

    /** Fast, side-effect-free predicate. */
    boolean matches(InboundMessage inbound);

    /** Produce the final reply body. Called iff {@link #matches} returned true. */
    String handle(InboundMessage inbound);

    /** Ordering hint. Lower first. Default 0. */
    default int order() {
        return 0;
    }
}
