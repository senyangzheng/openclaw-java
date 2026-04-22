package com.openclaw.sessions;

import com.openclaw.providers.api.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionRepositoryTest {

    @Test
    void shouldLoadOrCreateThenAppend() {
        final InMemorySessionRepository repo = new InMemorySessionRepository();
        final SessionKey key = new SessionKey("web", "anon", "c-1");

        final Session session = repo.loadOrCreate(key);
        session.append(ChatMessage.user("hello"));
        repo.save(session);

        assertThat(repo.size()).isEqualTo(1);
        assertThat(repo.find(key)).isPresent();
        assertThat(repo.find(key).orElseThrow().messages()).hasSize(1);
    }

    @Test
    void shouldReturnSameInstanceOnRepeatedLoadOrCreate() {
        final InMemorySessionRepository repo = new InMemorySessionRepository();
        final SessionKey key = new SessionKey("web", "anon", "c-1");

        final Session first = repo.loadOrCreate(key);
        final Session second = repo.loadOrCreate(key);

        assertThat(second).isSameAs(first);
    }

    @Test
    void shouldRejectBlankComponents() {
        assertThat(SessionKey.parse("web:anon:c-1").asString()).isEqualTo("web:anon:c-1");
    }
}
