-- Phase 2 / Sprint 2c: ad-hoc per-record share, the second layer alongside policies.
-- See analysis/03-cross-company-sharing.md § 3.4 — "share this one lead with partner Y,
-- WRITE, expires in 60 days".

CREATE TABLE record_shares (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_tenant_id     UUID NOT NULL REFERENCES tenants(id),
    consumer_tenant_id  UUID NOT NULL REFERENCES tenants(id),
    resource_type       VARCHAR(50) NOT NULL,
    resource_id         UUID NOT NULL,
    access_level        VARCHAR(20) NOT NULL CHECK (
        access_level IN ('NONE', 'READ', 'COMMENT', 'WRITE', 'MANAGE')
    ),
    shared_by           UUID NOT NULL REFERENCES users(id),
    expires_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_record_share UNIQUE (owner_tenant_id, consumer_tenant_id, resource_type, resource_id),
    CONSTRAINT chk_distinct_record_share_tenants CHECK (owner_tenant_id <> consumer_tenant_id)
);

CREATE INDEX idx_rs_lookup ON record_shares (consumer_tenant_id, resource_type, resource_id)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_rs_owner ON record_shares (owner_tenant_id, resource_type, resource_id)
    WHERE revoked_at IS NULL;
