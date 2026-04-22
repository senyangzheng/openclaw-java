package com.openclaw.providers.api;

import java.util.Objects;

/**
 * A single message in a chat conversation. M1 deliberately keeps this minimal;
 * tool-call / multimodal content types land in M2+.
 */
public record ChatMessage(Role role, String content) {

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static ChatMessage system(final String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(final String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(final String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public enum Role {
        SYSTEM, USER, ASSISTANT
    }
}
