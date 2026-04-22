-- V2: auth-profile vault (envelope-encrypted credentials)
-- Owned by openclaw-secrets. All writes MUST pass through JdbcAuthProfileVault so
-- the apiKey is AES-256-GCM sealed before touching MySQL.
--
-- Scheme:
--   data_ct/data_iv = AES-GCM(apiKey plaintext, DEK, data_iv)
--   dek_ct/dek_iv   = AES-GCM(DEK,             KEK, dek_iv)
-- The KEK is injected at runtime via OPENCLAW_SECRETS_KEK_BASE64 and never stored.

CREATE TABLE IF NOT EXISTS oc_auth_profile (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_id   VARCHAR(64)     NOT NULL COMMENT 'qwen | google | ...',
    profile_id    VARCHAR(128)    NOT NULL COMMENT 'stable short id within providerId',

    data_ct       VARBINARY(2048) NOT NULL COMMENT 'AES-GCM ciphertext of apiKey',
    data_iv       VARBINARY(16)   NOT NULL COMMENT '12-byte GCM IV for data_ct',
    dek_ct        VARBINARY(128)  NOT NULL COMMENT 'AES-GCM ciphertext of the DEK',
    dek_iv        VARBINARY(16)   NOT NULL COMMENT '12-byte GCM IV for dek_ct',

    extras_json   MEDIUMTEXT      NULL     COMMENT 'plain-text JSON map of non-secret hints',

    created_at    DATETIME(3)     NOT NULL,
    updated_at    DATETIME(3)     NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_profile (provider_id, profile_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'openclaw: envelope-encrypted provider credentials';
