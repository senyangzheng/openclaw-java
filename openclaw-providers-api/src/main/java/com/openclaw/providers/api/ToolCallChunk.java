package com.openclaw.providers.api;

import java.util.Objects;

/**
 * Incremental tool-call payload observed on a streaming response. Providers emit
 * one of these per delta (OpenAI / Qwen chunk the JSON arguments across frames,
 * Gemini emits a single self-contained call).
 *
 * @param id              provider-assigned call id (stable across chunks of the same call)
 * @param index           position in the {@code tool_calls} array (0-based)
 * @param name            function name — may be {@code null} on subsequent argument-only chunks
 * @param argumentsDelta  partial JSON arguments to append to the in-progress buffer — may be empty
 */
public record ToolCallChunk(String id, int index, String name, String argumentsDelta) {

    public ToolCallChunk {
        Objects.requireNonNull(id, "id");
        argumentsDelta = argumentsDelta == null ? "" : argumentsDelta;
    }
}
