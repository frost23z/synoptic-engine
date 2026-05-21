-- V011__rls_baseline.sql
--
-- Phase-1c follow-up: extend Postgres RLS coverage to every tenant-scoped table.
--
-- Background:
--   V007 only enabled RLS on the four tables that participate in cross-tenant
--   sharing today (leads, persons, organizations, products). Every other
--   table holding `tenant_id` data relied on the Hibernate `@Filter("tenantFilter")`
--   as its only isolation layer. That filter only rewrites HQL/JPQL/Criteria —
--   *not* native SQL — so any native query, batch job running with
--   `TenantContext = null`, or BYPASSRLS=false role would silently leak rows
--   across tenants.
--
-- This migration installs the standard tenant policy on the remaining 33
-- BaseEntity-backed tables. The policy template mirrors V007:
--
--   (app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant())
--
-- The `IS NULL` escape preserves null-tenant flows that are intentional
-- (bootstrap, public endpoints, scheduled workers that set context manually).
--
-- For tables that are *also* sharable resource families (today: quotes,
-- warehouses, activities — see `sharing/domain/ResourceType.kt`), a second
-- clause routes through `app_has_visibility(resource_type, id, level)` so the
-- materialized resource_visibility table grants effective cross-tenant access
-- exactly like V007 does for leads/persons/organizations/products.
--
-- The seven sharing-module tables (cross_tenant_audit, record_shares,
-- resource_visibility, share_materialization_queue, tenant_relationships,
-- tenant_share_policies) are *intentionally* cross-tenant — they are not
-- BaseEntity subclasses, have no `tenant_id` column, and authorization is done
-- at the service layer. They are deliberately excluded.
--
-- Note for the test suite: Testcontainers' default Postgres user is BYPASSRLS,
-- so these policies do not fire under integration tests. Hibernate `@Filter`
-- remains the test-time isolation. The policies start firing in production
-- under the application's `synoptic_app` role (NOBYPASSRLS=true).

-- ── Identity (V001) ─────────────────────────────────────────────────────────
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_users_rw ON public.users
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_roles_rw ON public.roles
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_groups_rw ON public.groups
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- ── CRM core (V002) ─────────────────────────────────────────────────────────
-- Pipelines / stages: pure tenant data, not directly sharable.
ALTER TABLE public.pipelines ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_pipelines_rw ON public.pipelines
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.stages ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_stages_rw ON public.stages
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- Activities: enumerated as a shareable ResourceType (literal 'leads.activities')
-- though materialization for it is not wired yet (worker returns null for the
-- physical table mapping). Use the visibility-aware policy now so when sharing
-- is wired the policy already routes correctly.
ALTER TABLE public.activities ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_activities_read ON public.activities FOR SELECT
    USING (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('leads.activities'::character varying, id, 'READ'::character varying)
    );
CREATE POLICY rls_activities_write ON public.activities
    USING (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('leads.activities'::character varying, id, 'WRITE'::character varying)
    )
    WITH CHECK (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('leads.activities'::character varying, id, 'WRITE'::character varying)
    );

-- Activity sub-rows follow the parent — no independent share semantics.
ALTER TABLE public.activity_files ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_activity_files_rw ON public.activity_files
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.activity_participants ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_activity_participants_rw ON public.activity_participants
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- Lead-related lookup tables.
ALTER TABLE public.lead_products ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_lead_products_rw ON public.lead_products
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.lead_sources ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_lead_sources_rw ON public.lead_sources
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.lead_types ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_lead_types_rw ON public.lead_types
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- Tags are tenant-scoped catalogues; the join tables (lead_tags, person_tags,
-- product_tags, email_tags, warehouse_tags) have no tenant_id of their own and
-- are protected via FK to a tenant-scoped parent — they inherit isolation
-- through the parent's policy when joined; standalone SELECTs on those join
-- tables are unusual.
ALTER TABLE public.tags ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_tags_rw ON public.tags
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- Quotes: sharable resource type ('quotes').
ALTER TABLE public.quotes ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_quotes_read ON public.quotes FOR SELECT
    USING (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('quotes'::character varying, id, 'READ'::character varying)
    );
CREATE POLICY rls_quotes_write ON public.quotes
    USING (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('quotes'::character varying, id, 'WRITE'::character varying)
    )
    WITH CHECK (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('quotes'::character varying, id, 'WRITE'::character varying)
    );

ALTER TABLE public.quote_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_quote_items_rw ON public.quote_items
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- ── Inventory (V003) ────────────────────────────────────────────────────────
-- Warehouses are sharable ResourceType.WAREHOUSES.
ALTER TABLE public.warehouses ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_warehouses_read ON public.warehouses FOR SELECT
    USING (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('warehouses'::character varying, id, 'READ'::character varying)
    );
CREATE POLICY rls_warehouses_write ON public.warehouses
    USING (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('warehouses'::character varying, id, 'WRITE'::character varying)
    )
    WITH CHECK (
        (public.app_current_tenant() IS NULL)
        OR (tenant_id = public.app_current_tenant())
        OR public.app_has_visibility('warehouses'::character varying, id, 'WRITE'::character varying)
    );

ALTER TABLE public.warehouse_locations ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_warehouse_locations_rw ON public.warehouse_locations
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.product_inventories ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_product_inventories_rw ON public.product_inventories
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- ── Email (V004) ────────────────────────────────────────────────────────────
ALTER TABLE public.emails ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_emails_rw ON public.emails
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.email_attachments ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_email_attachments_rw ON public.email_attachments
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- ── Settings + automation (V005) ────────────────────────────────────────────
ALTER TABLE public.attributes ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_attributes_rw ON public.attributes
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.attribute_options ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_attribute_options_rw ON public.attribute_options
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.attribute_values ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_attribute_values_rw ON public.attribute_values
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.data_imports ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_data_imports_rw ON public.data_imports
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.email_templates ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_email_templates_rw ON public.email_templates
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.marketing_campaigns ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_marketing_campaigns_rw ON public.marketing_campaigns
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.marketing_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_marketing_events_rw ON public.marketing_events
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- system_configs has a tenant_id column at the SQL level but the JPA entity
-- (SystemConfig.kt) does not currently model it — the PK is `code` alone, so
-- writes from one tenant overwrite another's. RLS will not fix that bug, but
-- it does at least keep cross-tenant *reads* honest until the JPA mapping is
-- corrected. Tracked separately as audit finding H9.
ALTER TABLE public.system_configs ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_system_configs_rw ON public.system_configs
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.web_forms ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_web_forms_rw ON public.web_forms
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.web_form_attributes ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_web_form_attributes_rw ON public.web_form_attributes
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.webhooks ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_webhooks_rw ON public.webhooks
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.workflows ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_workflows_rw ON public.workflows
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

ALTER TABLE public.workflow_action_runs ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_workflow_action_runs_rw ON public.workflow_action_runs
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- ── Datagrid (V006) ─────────────────────────────────────────────────────────
ALTER TABLE public.datagrid_saved_filters ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_datagrid_saved_filters_rw ON public.datagrid_saved_filters
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));

-- ── Webhook delivery runs (V010) ────────────────────────────────────────────
ALTER TABLE public.webhook_delivery_runs ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_webhook_delivery_runs_rw ON public.webhook_delivery_runs
    USING ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()))
    WITH CHECK ((public.app_current_tenant() IS NULL) OR (tenant_id = public.app_current_tenant()));
