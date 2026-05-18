-- Phase 1b / P1.10: extend soft delete to the entities that still hard-delete today.
-- The matching entities are switched to `SoftDeletable` in the same PR and get
-- `@SQLDelete` + `@SQLRestriction` so any future repository.delete() call cannot
-- accidentally hard-delete and any JPA query automatically filters tombstones.

ALTER TABLE emails              ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE email_attachments   ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE email_templates     ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE web_forms           ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE workflows           ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE webhooks            ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE marketing_events    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE attributes          ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_emails_not_deleted              ON emails              (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_email_attachments_not_deleted   ON email_attachments   (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_email_templates_not_deleted     ON email_templates     (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_web_forms_not_deleted           ON web_forms           (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_workflows_not_deleted           ON workflows           (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_webhooks_not_deleted            ON webhooks            (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_marketing_campaigns_not_deleted ON marketing_campaigns (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_marketing_events_not_deleted    ON marketing_events    (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_attributes_not_deleted          ON attributes          (id) WHERE deleted_at IS NULL;
