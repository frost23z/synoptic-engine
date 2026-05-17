-- Phase 0 / P0-2: per-tenant unique constraints.
-- Replaces globally-unique name columns with composite (tenant_id, name) constraints
-- so two tenants can each have a role named "ADMIN", a tag named "VIP", etc.
--
-- User.email stays globally unique to keep the login flow simple (no tenant picker on login).
-- email_templates, marketing_events, marketing_campaigns get per-tenant uniqueness too.

ALTER TABLE roles DROP CONSTRAINT IF EXISTS uq_roles_name;
ALTER TABLE roles ADD CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE groups DROP CONSTRAINT IF EXISTS uq_groups_name;
ALTER TABLE groups ADD CONSTRAINT uq_groups_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE tags DROP CONSTRAINT IF EXISTS uq_tags_name;
ALTER TABLE tags ADD CONSTRAINT uq_tags_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE lead_sources DROP CONSTRAINT IF EXISTS uq_lead_sources_name;
ALTER TABLE lead_sources ADD CONSTRAINT uq_lead_sources_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE lead_types DROP CONSTRAINT IF EXISTS uq_lead_types_name;
ALTER TABLE lead_types ADD CONSTRAINT uq_lead_types_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE email_templates DROP CONSTRAINT IF EXISTS uq_email_templates_name;
ALTER TABLE email_templates ADD CONSTRAINT uq_email_templates_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE marketing_events DROP CONSTRAINT IF EXISTS uq_marketing_events_name;
ALTER TABLE marketing_events ADD CONSTRAINT uq_marketing_events_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE marketing_campaigns DROP CONSTRAINT IF EXISTS uq_marketing_campaigns_name;
ALTER TABLE marketing_campaigns ADD CONSTRAINT uq_marketing_campaigns_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE products DROP CONSTRAINT IF EXISTS uq_products_sku;
ALTER TABLE products ADD CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku);
