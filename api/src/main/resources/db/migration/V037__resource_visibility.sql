-- Phase 2 / Sprint 2b: materialized visibility index.
-- One row per (consumer_tenant, resource_type, resource_id, source, source_id) tuple.
-- Populated by triggers when policies materialize or when record_shares are created
-- (Sprint 2c). Refreshable from source tables if it ever drifts.
--
-- See analysis/03-cross-company-sharing.md § 4.2 ("Why a separate resource_visibility table")
-- and § 13 ("Performance plan").

CREATE TABLE resource_visibility (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_tenant_id     UUID NOT NULL,
    consumer_tenant_id  UUID NOT NULL,
    resource_type       VARCHAR(50) NOT NULL,
    resource_id         UUID NOT NULL,
    access_level        VARCHAR(20) NOT NULL CHECK (
        access_level IN ('NONE', 'READ', 'COMMENT', 'WRITE', 'MANAGE')
    ),
    source              VARCHAR(20) NOT NULL CHECK (source IN ('policy', 'record', 'cascade')),
    source_id           UUID NOT NULL,
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_visibility UNIQUE (consumer_tenant_id, resource_type, resource_id, source, source_id)
);

-- Primary lookup pattern (used by RLS USING-clause): consumer + resource lookup.
CREATE INDEX idx_rv_lookup ON resource_visibility (consumer_tenant_id, resource_type, resource_id);
-- Reverse lookup for revocation cleanup when an owner soft/hard-deletes a record.
CREATE INDEX idx_rv_owner ON resource_visibility (owner_tenant_id, resource_type, resource_id);
-- Expiry sweep.
CREATE INDEX idx_rv_expiry ON resource_visibility (expires_at) WHERE expires_at IS NOT NULL;
