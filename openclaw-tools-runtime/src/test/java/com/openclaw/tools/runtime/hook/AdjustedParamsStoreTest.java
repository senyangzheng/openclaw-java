package com.openclaw.tools.runtime.hook;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdjustedParamsStoreTest {

    @Test
    @DisplayName("put + consume is a single-shot fetch-and-remove")
    void putThenConsume() {
        final AdjustedParamsStore store = new AdjustedParamsStore();
        store.put("call-1", Map.of("a", 1));
        assertThat(store.consumeForToolCall("call-1")).hasValue(Map.of("a", 1));
        assertThat(store.consumeForToolCall("call-1")).isEmpty();
    }

    @Test
    @DisplayName("peek is non-destructive")
    void peek() {
        final AdjustedParamsStore store = new AdjustedParamsStore();
        store.put("call-1", Map.of("a", 1));
        assertThat(store.peek("call-1")).hasValue(Map.of("a", 1));
        assertThat(store.peek("call-1")).hasValue(Map.of("a", 1));
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("LRU eviction drops oldest entries when capacity exceeded")
    void lruEviction() {
        final AdjustedParamsStore store = new AdjustedParamsStore(2);
        store.put("a", Map.of("x", 1));
        store.put("b", Map.of("x", 2));
        store.put("c", Map.of("x", 3)); // evicts "a"
        assertThat(store.peek("a")).isEmpty();
        assertThat(store.peek("b")).isPresent();
        assertThat(store.peek("c")).isPresent();
    }

    @Test
    @DisplayName("null toolCallId on consume returns empty instead of throwing")
    void nullConsumeSafe() {
        final AdjustedParamsStore store = new AdjustedParamsStore();
        assertThat(store.consumeForToolCall(null)).isEmpty();
    }
}
