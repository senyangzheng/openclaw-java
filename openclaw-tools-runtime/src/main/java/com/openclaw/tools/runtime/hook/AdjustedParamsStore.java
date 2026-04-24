package com.openclaw.tools.runtime.hook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Correlates {@code before_tool_call} hook outputs with the later {@code after_tool_call} hook on the same
 * {@code toolCallId}. Mirrors openclaw-ts {@code adjustedParamsByToolCallId} + {@code
 * consumeAdjustedParamsForToolCall} (see {@code agents/pi-tools.before-tool-call.ts}).
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@link #put(String, Map)} records the rewritten params. The most recent call for a given id wins.</li>
 *   <li>{@link #consumeForToolCall(String)} performs a single-shot fetch-and-remove; after-hook emitters MUST
 *       use this (not the raw params) so they see the post-hook values.</li>
 *   <li>A bounded LRU eviction protects against runaway cardinality — default max entries
 *       {@value #DEFAULT_MAX_TRACKED_ADJUSTED_PARAMS} ({@code MAX_TRACKED_ADJUSTED_PARAMS} in ts).
 *       Oldest entries are evicted on overflow; evicted ids silently lose their record, which is correct —
 *       the after-hook emitter falls back to the raw params in that case.</li>
 * </ul>
 *
 * <p>Thread-safety: all public methods are {@code synchronized} on {@code this}. The store is touched twice
 * per tool call (once on before-hook merge, once on after-hook consume) so contention is negligible.
 */
public final class AdjustedParamsStore {

    public static final int DEFAULT_MAX_TRACKED_ADJUSTED_PARAMS = 4096;

    private final int maxEntries;
    /** Access-ordered so eldest entry is the least-recently touched. */
    private final LinkedHashMap<String, Map<String, Object>> entries;

    public AdjustedParamsStore() {
        this(DEFAULT_MAX_TRACKED_ADJUSTED_PARAMS);
    }

    public AdjustedParamsStore(final int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        this.maxEntries = maxEntries;
        this.entries = new LinkedHashMap<>(Math.min(64, maxEntries), 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, Map<String, Object>> eldest) {
                return size() > AdjustedParamsStore.this.maxEntries;
            }
        };
    }

    /** Record the hook-rewritten params for the given tool call id. */
    public synchronized void put(final String toolCallId, final Map<String, Object> params) {
        Objects.requireNonNull(toolCallId, "toolCallId");
        final Map<String, Object> copy = params == null ? Map.of() : Map.copyOf(params);
        entries.put(toolCallId, copy);
    }

    /**
     * Fetch and remove the previously recorded params. Returns {@link Optional#empty()} when
     * {@link #put(String, Map)} was never called for this id (or when it has been evicted).
     */
    public synchronized Optional<Map<String, Object>> consumeForToolCall(final String toolCallId) {
        if (toolCallId == null) {
            return Optional.empty();
        }
        final Map<String, Object> removed = entries.remove(toolCallId);
        return Optional.ofNullable(removed);
    }

    /**
     * Read-only lookup that does NOT remove the entry — for diagnostics / tests only. Production code should
     * use {@link #consumeForToolCall(String)}.
     */
    public synchronized Optional<Map<String, Object>> peek(final String toolCallId) {
        return Optional.ofNullable(entries.get(toolCallId))
                .map(Collections::unmodifiableMap);
    }

    public synchronized int size() {
        return entries.size();
    }

    public int capacity() {
        return maxEntries;
    }
}
