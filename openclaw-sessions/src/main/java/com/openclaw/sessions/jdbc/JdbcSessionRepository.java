package com.openclaw.sessions.jdbc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.sessions.Session;
import com.openclaw.sessions.SessionKey;
import com.openclaw.sessions.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-Plus backed {@link SessionRepository}. Writes are append-only: {@link #save(Session)}
 * computes the DB-side message count and only inserts rows with {@code seq >= existing}.
 * Read path uses a small Caffeine cache for {@code sessionKey → sessionId} lookups
 * to avoid a second-order SELECT on the hot path of {@code appendMessage → save}.
 */
public class JdbcSessionRepository implements SessionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcSessionRepository.class);

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final Cache<String, Long> keyToIdCache;

    public JdbcSessionRepository(final SessionMapper sessionMapper,
                                 final MessageMapper messageMapper,
                                 final Cache<String, Long> keyToIdCache) {
        this.sessionMapper = Objects.requireNonNull(sessionMapper, "sessionMapper");
        this.messageMapper = Objects.requireNonNull(messageMapper, "messageMapper");
        this.keyToIdCache = Objects.requireNonNull(keyToIdCache, "keyToIdCache");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> find(final SessionKey key) {
        Objects.requireNonNull(key, "key");
        return findEntity(key).map(entity -> hydrate(key, entity));
    }

    @Override
    @Transactional
    public Session loadOrCreate(final SessionKey key) {
        Objects.requireNonNull(key, "key");
        return findEntity(key)
            .map(entity -> hydrate(key, entity))
            .orElseGet(() -> createAndHydrate(key));
    }

    @Override
    @Transactional
    public void save(final Session session) {
        Objects.requireNonNull(session, "session");
        final SessionKey key = session.key();
        final SessionEntity entity = findEntity(key).orElseGet(() -> insertEntity(key));
        final Long sessionId = entity.getId();

        final List<ChatMessage> all = session.messages();
        final int existing = messageMapper.countBySession(sessionId);
        if (all.size() < existing) {
            // Defensive: in-memory session is behind storage. Refuse to truncate history.
            log.warn("session.save.shrink.ignored key={} inMemory={} persisted={} — nothing written",
                key.asString(), all.size(), existing);
            return;
        }
        if (all.size() == existing) {
            touchUpdatedAt(entity);
            return;
        }

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (int i = existing; i < all.size(); i++) {
            final ChatMessage msg = all.get(i);
            final MessageEntity me = new MessageEntity();
            me.setSessionId(sessionId);
            me.setSeq(i);
            me.setRole(msg.role().name());
            me.setContent(msg.content());
            me.setCreatedAt(now);
            messageMapper.insert(me);
        }
        touchUpdatedAt(entity);
        log.debug("session.save key={} appended={} total={}", key.asString(), all.size() - existing, all.size());
    }

    @Override
    @Transactional
    public void delete(final SessionKey key) {
        Objects.requireNonNull(key, "key");
        findEntity(key).ifPresent(entity -> {
            // oc_message has ON DELETE CASCADE
            sessionMapper.deleteById(entity.getId());
            keyToIdCache.invalidate(key.asString());
        });
    }

    private Optional<SessionEntity> findEntity(final SessionKey key) {
        final Long cached = keyToIdCache.getIfPresent(key.asString());
        if (cached != null) {
            final SessionEntity byId = sessionMapper.selectById(cached);
            if (byId != null) {
                return Optional.of(byId);
            }
            keyToIdCache.invalidate(key.asString());
        }
        final LambdaQueryWrapper<SessionEntity> q = new LambdaQueryWrapper<SessionEntity>()
            .eq(SessionEntity::getSessionKey, key.asString());
        final SessionEntity entity = sessionMapper.selectOne(q);
        if (entity != null) {
            keyToIdCache.put(key.asString(), entity.getId());
        }
        return Optional.ofNullable(entity);
    }

    /**
     * Race-safe insert. Two concurrent {@code loadOrCreate} calls for the same {@link SessionKey} both
     * see "no row yet", both attempt the insert — one succeeds, the other violates the
     * {@code uk_session_key} unique constraint. That second thread catches
     * {@link DataIntegrityViolationException} and re-selects the now-present row. The alternative
     * (pessimistic SELECT ... FOR UPDATE) would serialize every session-load DB hop and defeats
     * session-lane's purpose of scaling cross-session traffic.
     *
     * <p>Note: session-lane currently only serializes <em>agent execution</em>, not
     * {@code loadOrCreate/save}. A broader refactor (session I/O inside the lane) is tracked for
     * post-M3; until then, duplicate-key is the correct graceful-degradation point.
     */
    private SessionEntity insertEntity(final SessionKey key) {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final SessionEntity entity = new SessionEntity();
        entity.setSessionKey(key.asString());
        entity.setChannelId(key.channelId());
        entity.setAccountId(key.accountId());
        entity.setConversationId(key.conversationId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            sessionMapper.insert(entity);
        } catch (DataIntegrityViolationException dup) {
            log.debug("session.insert.duplicate key={} — re-selecting after concurrent create",
                key.asString());
            keyToIdCache.invalidate(key.asString());
            return selectFresh(key).orElseThrow(() -> new IllegalStateException(
                "session vanished after duplicate key: " + key.asString(), dup));
        }
        keyToIdCache.put(key.asString(), entity.getId());
        return entity;
    }

    private Optional<SessionEntity> selectFresh(final SessionKey key) {
        final LambdaQueryWrapper<SessionEntity> q = new LambdaQueryWrapper<SessionEntity>()
            .eq(SessionEntity::getSessionKey, key.asString());
        final SessionEntity entity = sessionMapper.selectOne(q);
        if (entity != null) {
            keyToIdCache.put(key.asString(), entity.getId());
        }
        return Optional.ofNullable(entity);
    }

    private Session createAndHydrate(final SessionKey key) {
        final SessionEntity entity = insertEntity(key);
        return new Session(key, toInstant(entity.getCreatedAt()), toInstant(entity.getUpdatedAt()), List.of());
    }

    private Session hydrate(final SessionKey key, final SessionEntity entity) {
        final List<MessageEntity> rows = messageMapper.selectBySessionOrderBySeq(entity.getId());
        final List<ChatMessage> history = rows.stream()
            .map(r -> new ChatMessage(ChatMessage.Role.valueOf(r.getRole()), r.getContent()))
            .toList();
        return new Session(key, toInstant(entity.getCreatedAt()), toInstant(entity.getUpdatedAt()), history);
    }

    private void touchUpdatedAt(final SessionEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        sessionMapper.updateById(entity);
    }

    private static Instant toInstant(final LocalDateTime ldt) {
        return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
    }
}
