-- Phase 2 / Sprint 2b: Postgres Row-Level Security policies as the authoritative tenant
-- boundary. Defense-in-depth against any future code path that bypasses the Hibernate
-- @Filter — native queries, ad-hoc reports, replicas.
--
-- See § P2-2 of analysis/07-verification-findings.md for why this matters and
-- analysis/03-cross-company-sharing.md § 5.1 for the policy shape.
--
-- Deployment model:
--   - synoptic_owner role (BYPASSRLS = true) — Flyway, BootstrapService, ops scripts.
--   - synoptic_app   role (BYPASSRLS = false) — runtime application connection.
--   - Per transaction the application runs `SET LOCAL app.current_tenant = '<uuid>'`
--     so the policy USING-clause resolves.
--
-- Tests run as Postgres superuser (testcontainers default) which is BYPASSRLS, so
-- these policies don't kick in there — the Hibernate @Filter is the runtime
-- isolation in tests. The migration is enforced by a future deploy step that
-- creates the two roles and sets up a SessionConnectionInitializer; that's a
-- Phase 2.5 ops task tracked separately.
--
-- Each policy is "own OR shared". The shared half is the EXISTS lookup against
-- resource_visibility populated by Sprint 2b's materialization worker.

CREATE OR REPLACE FUNCTION app_current_tenant() RETURNS UUID
    LANGUAGE sql STABLE AS
$$
    SELECT NULLIF(current_setting('app.current_tenant', true), '')::uuid
$$;

CREATE OR REPLACE FUNCTION app_has_visibility(
    p_resource_type VARCHAR,
    p_resource_id   UUID,
    p_min_level     VARCHAR DEFAULT 'READ'
) RETURNS BOOLEAN
    LANGUAGE sql STABLE AS
$$
    SELECT EXISTS (
        SELECT 1
        FROM resource_visibility v
        WHERE v.consumer_tenant_id = app_current_tenant()
          AND v.resource_type = p_resource_type
          AND v.resource_id = p_resource_id
          AND (v.expires_at IS NULL OR v.expires_at > NOW())
          AND (
              CASE p_min_level
                  WHEN 'READ'    THEN v.access_level IN ('READ','COMMENT','WRITE','MANAGE')
                  WHEN 'COMMENT' THEN v.access_level IN ('COMMENT','WRITE','MANAGE')
                  WHEN 'WRITE'   THEN v.access_level IN ('WRITE','MANAGE')
                  WHEN 'MANAGE'  THEN v.access_level IN ('MANAGE')
              END
          )
    )
$$;

-- LEADS
ALTER TABLE leads ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_leads_read ON leads
    FOR SELECT
    USING (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('leads', id, 'READ')
    );
CREATE POLICY rls_leads_write ON leads
    FOR ALL
    USING (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('leads', id, 'WRITE')
    )
    WITH CHECK (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('leads', id, 'WRITE')
    );

-- PERSONS
ALTER TABLE persons ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_persons_read ON persons
    FOR SELECT
    USING (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('contacts.persons', id, 'READ')
    );
CREATE POLICY rls_persons_write ON persons
    FOR ALL
    USING (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('contacts.persons', id, 'WRITE')
    )
    WITH CHECK (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('contacts.persons', id, 'WRITE')
    );

-- ORGANIZATIONS
ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_organizations_read ON organizations
    FOR SELECT
    USING (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('contacts.organizations', id, 'READ')
    );
CREATE POLICY rls_organizations_write ON organizations
    FOR ALL
    USING (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('contacts.organizations', id, 'WRITE')
    )
    WITH CHECK (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('contacts.organizations', id, 'WRITE')
    );

-- PRODUCTS
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_products_read ON products
    FOR SELECT
    USING (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('products', id, 'READ')
    );
CREATE POLICY rls_products_write ON products
    FOR ALL
    USING (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('products', id, 'WRITE')
    )
    WITH CHECK (
        app_current_tenant() IS NULL
        OR tenant_id = app_current_tenant()
        OR app_has_visibility('products', id, 'WRITE')
    );
