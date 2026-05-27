-- T3.4 — Append-only audit log for sensitive same-tenant mutations.
--
-- Captures CREATE / UPDATE / DELETE operations on entities whose changes
-- should be visible in a compliance or operations review:
--   • system_configs   (may contain credentials / API keys)
--   • webhooks         (signed payloads go to tenant-supplied URLs)
--
-- Intentionally NOT a BaseEntity table:
--   • No tenant filter — tenant_id is an explicit column, not a filter param.
--   • No soft-delete — audit rows must be immutable; hard-delete is only ever
--     done by a retention worker with a configurable grace period.
--   • No @Version — append-only, never updated.
--
-- RLS: enabled with a simple tenant_id predicate so tenants can only read their
-- own audit rows, but the write path (Spring service code) uses BYPASSRLS to
-- ensure audit rows are always written even if a misconfiguration would
-- otherwise block them.

CREATE TABLE audit_logs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL,
    actor_id    UUID,                              -- NULL for system / scheduled actions
    entity_type VARCHAR(100) NOT NULL,
    entity_id   VARCHAR(255),                     -- UUID or business key (config code)
    action      VARCHAR(50)  NOT NULL,             -- CREATE | UPDATE | DELETE
    payload     JSONB,                             -- sanitized change summary (no secrets)
    at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for compliance review (most common query: all actions on a tenant, newest first).
CREATE INDEX idx_audit_logs_tenant_at ON audit_logs (tenant_id, at DESC);
-- Index for per-entity audit timeline.
CREATE INDEX idx_audit_logs_entity ON audit_logs (tenant_id, entity_type, entity_id, at DESC);

-- RLS: tenants can only read their own audit rows.
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_audit_logs_rw ON audit_logs
    USING  ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()))
    WITH CHECK ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()));
