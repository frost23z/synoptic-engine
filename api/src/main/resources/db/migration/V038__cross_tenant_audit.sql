-- Phase 2 / Sprint 2b: cross-tenant audit log. Every action where the actor's tenant
-- differs from the record's owner tenant lands here. Used by the owner to see who
-- touched their record after a share was granted.
--
-- Indexes match the two main query patterns: "show me everything that happened to
-- my record" and "show me everything tenant X did".

CREATE TABLE cross_tenant_audit (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_tenant_id     UUID NOT NULL,
    actor_tenant_id     UUID NOT NULL,
    actor_user_id       UUID NOT NULL,
    resource_type       VARCHAR(50) NOT NULL,
    resource_id         UUID NOT NULL,
    action              VARCHAR(40) NOT NULL,
    payload_jsonb       JSONB,
    at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cta_owner_resource ON cross_tenant_audit (owner_tenant_id, resource_type, resource_id, at DESC);
CREATE INDEX idx_cta_actor ON cross_tenant_audit (actor_tenant_id, at DESC);
