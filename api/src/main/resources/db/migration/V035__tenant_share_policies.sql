-- Phase 2 / Sprint 2a: policy = "share all of resource X with this tenant at this access level".
-- One row per (relationship, resource_type). filter_jsonb scopes which records the policy
-- applies to (optional). cascade_jsonb overrides the default cascade rules from § 7.
-- No enforcement yet — data only in Sprint 2a. Sprint 2b adds the visibility materialization.

CREATE TABLE tenant_share_policies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    relationship_id     UUID NOT NULL REFERENCES tenant_relationships(id) ON DELETE CASCADE,
    resource_type       VARCHAR(50) NOT NULL,
    access_level        VARCHAR(20) NOT NULL CHECK (
        access_level IN ('NONE', 'READ', 'COMMENT', 'WRITE', 'MANAGE')
    ),
    filter_jsonb        JSONB,
    cascade_jsonb       JSONB,
    materialize         BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_share_policy UNIQUE (relationship_id, resource_type)
);

CREATE INDEX idx_tsp_relationship ON tenant_share_policies (relationship_id)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_tsp_resource_type ON tenant_share_policies (resource_type)
    WHERE revoked_at IS NULL;
