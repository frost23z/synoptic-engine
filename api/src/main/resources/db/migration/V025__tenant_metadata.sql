-- Phase 0 / P0-3: bring `tenants` up to what the application needs.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS status      VARCHAR(20)
        NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED')),
    ADD COLUMN IF NOT EXISTS legal_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS timezone    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS locale      VARCHAR(16),
    ADD COLUMN IF NOT EXISTS deleted_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version     BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_tenants_status_active
    ON tenants (status)
    WHERE deleted_at IS NULL;
