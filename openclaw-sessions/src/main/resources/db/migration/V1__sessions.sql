-- V1: base session storage
-- Owned by openclaw-sessions. Any module persisting conversational state MUST go
-- through SessionRepository, not these tables directly.

CREATE TABLE IF NOT EXISTS oc_session (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_key     VARCHAR(300)    NOT NULL COMMENT 'channelId:accountId:conversationId, stable PK form',
    channel_id      VARCHAR(64)     NOT NULL,
    account_id      VARCHAR(128)    NOT NULL,
    conversation_id VARCHAR(128)    NOT NULL,
    created_at      DATETIME(3)     NOT NULL,
    updated_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_key (session_key),
    KEY idx_session_account (account_id, channel_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'openclaw: conversation sessions';

CREATE TABLE IF NOT EXISTS oc_message (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    seq        INT             NOT NULL COMMENT '0-based position within the session',
    role       VARCHAR(16)     NOT NULL COMMENT 'SYSTEM | USER | ASSISTANT',
    content    MEDIUMTEXT      NOT NULL,
    created_at DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_session_seq (session_id, seq),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id)
        REFERENCES oc_session (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'openclaw: per-session chat messages (append-only)';
