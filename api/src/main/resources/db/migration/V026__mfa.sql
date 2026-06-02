CREATE TABLE user_mfa_configs (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    totp_secret  TEXT         NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version      BIGINT       NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMPTZ
);

-- One active MFA config per user (soft-delete lets users re-enroll).
CREATE UNIQUE INDEX idx_mfa_configs_user_active ON user_mfa_configs (user_id) WHERE deleted_at IS NULL;

CREATE TABLE mfa_backup_codes (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash    VARCHAR(255) NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_mfa_backup_codes_user_unused ON mfa_backup_codes (user_id) WHERE used_at IS NULL;
