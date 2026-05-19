-- Phase 2 / Sprint 2a: directed edge between two tenants.
-- PARTNER relationships are modelled as two rows (one per direction). Directionality
-- matters because access policies and revocation rights are per-direction.
-- See analysis/03-cross-company-sharing.md § 3.1 and § 4.

CREATE TABLE tenant_relationships (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    relationship_type   VARCHAR(30) NOT NULL CHECK (
        relationship_type IN ('PARENT_CHILD', 'PARTNER', 'SUPPLIER_CLIENT')
    ),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (
        status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'REVOKED')
    ),
    initiated_by        UUID NOT NULL REFERENCES users(id),
    accepted_by         UUID REFERENCES users(id),
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at         TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenant_relationship UNIQUE (source_tenant_id, target_tenant_id, relationship_type),
    CONSTRAINT chk_distinct_tenants CHECK (source_tenant_id <> target_tenant_id)
);

CREATE INDEX idx_tr_source ON tenant_relationships (source_tenant_id, status);
CREATE INDEX idx_tr_target ON tenant_relationships (target_tenant_id, status);
