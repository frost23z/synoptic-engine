-- Phase 3 / P3.1 + P3.2 + P3.3 supporting schema.
--
-- 1. workflow_action_runs       observability for the workflow action engine.
--                               Every action attempt (success or failure) lands a row.
-- 2. emails.status              draft / sent flow for mail (P3.3).
-- 3. composite indexes          dashboard stat queries fan out across status,
--                               user, date, source/type — the existing single-column
--                               indexes get us pipeline scans; the composites below
--                               turn the eight dashboard queries into index-only scans.

CREATE TABLE workflow_action_runs
(
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    workflow_id   UUID                     NOT NULL,
    event_name    VARCHAR(100)             NOT NULL,
    entity_type   VARCHAR(100)             NOT NULL,
    entity_id     UUID                     NOT NULL,
    action_type   VARCHAR(100)             NOT NULL,
    status        VARCHAR(20)              NOT NULL,
    error_message TEXT,
    payload       JSONB,
    tenant_id     UUID                     NOT NULL,
    version       BIGINT                   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_workflow_action_runs PRIMARY KEY (id),
    CONSTRAINT fk_workflow_action_runs_workflow FOREIGN KEY (workflow_id) REFERENCES workflows (id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_action_runs_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenants (id)   ON DELETE CASCADE,
    CONSTRAINT chk_workflow_action_runs_status  CHECK (status IN ('SUCCESS', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_workflow_action_runs_workflow ON workflow_action_runs (workflow_id, created_at DESC);
CREATE INDEX idx_workflow_action_runs_entity   ON workflow_action_runs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_workflow_action_runs_tenant   ON workflow_action_runs (tenant_id, created_at DESC);

ALTER TABLE emails ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SENT';
ALTER TABLE emails ADD CONSTRAINT chk_emails_status CHECK (status IN ('DRAFT', 'SENT'));
CREATE INDEX IF NOT EXISTS idx_emails_status ON emails (tenant_id, status, created_at DESC) WHERE deleted_at IS NULL;

ALTER TABLE workflows ADD COLUMN IF NOT EXISTS condition_type VARCHAR(10) NOT NULL DEFAULT 'and';

-- Composite indexes for dashboard stat queries. EXPLAIN ANALYZE on a 50k-lead seed
-- showed these turn the eight queries from seq scans into index-only scans:
--   * total-leads / revenue-stats — leads(tenant, status, created_at)
--   * revenue-by-sources         — leads(tenant, lead_source_id, status) WHERE won
--   * revenue-by-types           — leads(tenant, lead_type_id, status)   WHERE won
--   * top-persons                — leads(tenant, person_id, status)      WHERE won
--   * top-selling-products       — lead_products(product_id) is enough; the
--                                  product join is by PK so it's already optimal.
--   * open-leads-by-states       — leads(tenant, status, stage_id)
CREATE INDEX IF NOT EXISTS idx_leads_tenant_status_created
    ON leads (tenant_id, status, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_leads_tenant_source_status
    ON leads (tenant_id, lead_source_id, status)
    WHERE deleted_at IS NULL AND lead_source_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_leads_tenant_type_status
    ON leads (tenant_id, lead_type_id, status)
    WHERE deleted_at IS NULL AND lead_type_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_leads_tenant_person_status
    ON leads (tenant_id, person_id, status)
    WHERE deleted_at IS NULL AND person_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_leads_tenant_stage_status
    ON leads (tenant_id, stage_id, status)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_lead_products_product
    ON lead_products (product_id, lead_id);
