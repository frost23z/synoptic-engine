-- V013__missing_composite_indexes.sql
--
-- Audit doc section 6.1 — fills in the composite `(tenant_id, …)` indexes
-- that hot list / join queries need. Without these, the queries fall back
-- to a sequential scan or a per-row tenant filter on top of a wider index.
--
-- All indexes use IF NOT EXISTS so the migration is safe to re-run after a
-- `flyway repair`.

-- ── Activities ──────────────────────────────────────────────────────────────
-- Owner / lead / person views all start by filtering tenant_id + one FK.
CREATE INDEX IF NOT EXISTS idx_activities_tenant_user
    ON public.activities (tenant_id, user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_activities_tenant_lead
    ON public.activities (tenant_id, lead_id)
    WHERE deleted_at IS NULL AND lead_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_activities_tenant_person
    ON public.activities (tenant_id, person_id)
    WHERE deleted_at IS NULL AND person_id IS NOT NULL;

-- ── Emails ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_emails_tenant_person
    ON public.emails (tenant_id, person_id)
    WHERE deleted_at IS NULL AND person_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_emails_tenant_lead
    ON public.emails (tenant_id, lead_id)
    WHERE deleted_at IS NULL AND lead_id IS NOT NULL;

-- ── Quotes ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_quotes_tenant_lead
    ON public.quotes (tenant_id, lead_id)
    WHERE deleted_at IS NULL AND lead_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_quotes_tenant_user_status
    ON public.quotes (tenant_id, user_id, status)
    WHERE deleted_at IS NULL;

-- ── Inventory ──────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_product_inventories_tenant_product_warehouse
    ON public.product_inventories (tenant_id, product_id, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_warehouse_locations_tenant_warehouse
    ON public.warehouse_locations (tenant_id, warehouse_id);

-- ── Datagrid saved filters ─────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_datagrid_filters_tenant_user_src
    ON public.datagrid_saved_filters (tenant_id, user_id, src);

-- ── Attributes ─────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_attribute_values_tenant_entity
    ON public.attribute_values (tenant_id, entity_id, entity_type);

CREATE INDEX IF NOT EXISTS idx_attribute_options_attribute
    ON public.attribute_options (attribute_id);
