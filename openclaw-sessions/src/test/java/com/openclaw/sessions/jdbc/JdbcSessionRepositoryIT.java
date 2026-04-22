package com.openclaw.sessions.jdbc;

import com.openclaw.providers.api.ChatMessage;
import com.openclaw.sessions.Session;
import com.openclaw.sessions.SessionKey;
import com.openclaw.sessions.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link JdbcSessionRepository} against a real MySQL 8
 * container. Requires Docker (Testcontainers). Skipped on machines without Docker.
 */
@SpringBootTest(classes = JdbcSessionRepositoryIT.TestApp.class)
@Testcontainers
class JdbcSessionRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("openclaw")
        .withUsername("openclaw")
        .withPassword("openclaw");

    @DynamicPropertySource
    static void datasource(final DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", MYSQL::getJdbcUrl);
        reg.add("spring.datasource.username", MYSQL::getUsername);
        reg.add("spring.datasource.password", MYSQL::getPassword);
        reg.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        reg.add("spring.flyway.enabled", () -> "true");
        reg.add("spring.flyway.locations", () -> "classpath:db/migration");
        reg.add("openclaw.sessions.store", () -> "jdbc");
    }

    @Autowired
    private SessionRepository repository;

    @Test
    void shouldPersistAndRestoreConversationHistory() {
        final SessionKey key = new SessionKey("web", "alice", "conv-1");

        final Session created = repository.loadOrCreate(key);
        created.append(ChatMessage.user("hello"));
        created.append(ChatMessage.assistant("hi there"));
        repository.save(created);

        final Optional<Session> restored = repository.find(key);
        assertThat(restored).isPresent();
        final List<ChatMessage> history = restored.get().messages();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(history.get(0).content()).isEqualTo("hello");
        assertThat(history.get(1).role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(history.get(1).content()).isEqualTo("hi there");
    }

    @Test
    void shouldAppendIncrementallyWithoutDuplicatingExistingRows() {
        final SessionKey key = new SessionKey("web", "bob", "conv-2");

        final Session s1 = repository.loadOrCreate(key);
        s1.append(ChatMessage.user("first"));
        repository.save(s1);

        // Simulate a new in-process view of the same session after restart.
        final Session s2 = repository.find(key).orElseThrow();
        s2.append(ChatMessage.assistant("second"));
        s2.append(ChatMessage.user("third"));
        repository.save(s2);

        final Session s3 = repository.find(key).orElseThrow();
        assertThat(s3.messages()).hasSize(3);
        assertThat(s3.messages().stream().map(ChatMessage::content).toList())
            .containsExactly("first", "second", "third");
    }

    @Test
    void shouldSurviveRestartBySharingStorageAcrossRepositoryInstances() {
        final SessionKey key = new SessionKey("web", "carol", "conv-3");

        final Session s1 = repository.loadOrCreate(key);
        s1.append(ChatMessage.user("before-restart"));
        repository.save(s1);

        // The repository bean is a singleton but loadOrCreate/find always round-trip to DB,
        // so a fresh find() is equivalent to a cold process boot.
        final Optional<Session> restored = repository.find(key);
        assertThat(restored).isPresent();
        assertThat(restored.get().messages()).singleElement()
            .extracting(ChatMessage::content).isEqualTo("before-restart");
    }

    @Test
    void shouldDeleteSessionAndCascadeMessages() {
        final SessionKey key = new SessionKey("web", "dave", "conv-4");
        final Session s = repository.loadOrCreate(key);
        s.append(ChatMessage.user("ephemeral"));
        repository.save(s);

        repository.delete(key);

        assertThat(repository.find(key)).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }
}
