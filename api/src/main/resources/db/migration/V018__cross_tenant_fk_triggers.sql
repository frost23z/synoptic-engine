-- V018__cross_tenant_fk_triggers.sql
-- Cross-tenant FK consistency triggers (T2.3).
--
-- For every FK edge between two tenant-scoped tables, add a BEFORE INSERT OR UPDATE
-- trigger that asserts the two rows' tenant_id values match. If the referenced row
-- does not exist IN THE SAME TENANT, the trigger raises an exception.
--
-- Design notes:
--  • Triggers are idempotent: CREATE OR REPLACE FUNCTION + DROP TRIGGER IF EXISTS.
--  • NULL FK columns represent optional relationships; the trigger fires only when
--    the FK column IS NOT NULL.
--  • Sharing-module tables (record_shares, resource_visibility, tenant_*,
--    cross_tenant_audit, share_materialization_queue) are intentionally cross-tenant
--    and do NOT get these triggers (guardrail: cross-tenant by design).
--  • RLS policies use "app_current_tenant() IS NULL OR ..." so null-tenant bootstrap
--    writes bypass RLS. Triggers must not block null-tenant writes either — they check
--    the referenced row's tenant_id against NEW.tenant_id; if NEW.tenant_id IS NULL
--    (public/bootstrap path) the check is skipped.
--  • ddl-auto = validate; this migration adds no columns, so entity mappings are
--    unchanged.

-- ── Helper: quotes → leads ────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_quote_tenant_matches_lead()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.lead_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM leads
          WHERE id = NEW.lead_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: quotes.lead_id % belongs to a different tenant than quotes.tenant_id %',
              NEW.lead_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_quote_tenant_matches_lead ON public.quotes;
CREATE TRIGGER trg_quote_tenant_matches_lead
    BEFORE INSERT OR UPDATE OF lead_id, tenant_id
    ON public.quotes
    FOR EACH ROW EXECUTE FUNCTION check_quote_tenant_matches_lead();

-- ── quote_items → quotes ──────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_quote_item_tenant_matches_quote()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.quote_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM quotes
          WHERE id = NEW.quote_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: quote_items.quote_id % belongs to a different tenant than quote_items.tenant_id %',
              NEW.quote_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_quote_item_tenant_matches_quote ON public.quote_items;
CREATE TRIGGER trg_quote_item_tenant_matches_quote
    BEFORE INSERT OR UPDATE OF quote_id, tenant_id
    ON public.quote_items
    FOR EACH ROW EXECUTE FUNCTION check_quote_item_tenant_matches_quote();

-- ── activities → leads ────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_activity_tenant_matches_lead()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.lead_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM leads
          WHERE id = NEW.lead_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: activities.lead_id % belongs to a different tenant than activities.tenant_id %',
              NEW.lead_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_activity_tenant_matches_lead ON public.activities;
CREATE TRIGGER trg_activity_tenant_matches_lead
    BEFORE INSERT OR UPDATE OF lead_id, tenant_id
    ON public.activities
    FOR EACH ROW EXECUTE FUNCTION check_activity_tenant_matches_lead();

-- ── activities → persons ──────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_activity_tenant_matches_person()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.person_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM persons
          WHERE id = NEW.person_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: activities.person_id % belongs to a different tenant than activities.tenant_id %',
              NEW.person_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_activity_tenant_matches_person ON public.activities;
CREATE TRIGGER trg_activity_tenant_matches_person
    BEFORE INSERT OR UPDATE OF person_id, tenant_id
    ON public.activities
    FOR EACH ROW EXECUTE FUNCTION check_activity_tenant_matches_person();

-- ── activities → organizations ────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_activity_tenant_matches_organization()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organization_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM organizations
          WHERE id = NEW.organization_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: activities.organization_id % belongs to a different tenant than activities.tenant_id %',
              NEW.organization_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_activity_tenant_matches_organization ON public.activities;
CREATE TRIGGER trg_activity_tenant_matches_organization
    BEFORE INSERT OR UPDATE OF organization_id, tenant_id
    ON public.activities
    FOR EACH ROW EXECUTE FUNCTION check_activity_tenant_matches_organization();

-- ── lead_products → leads ─────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_lead_product_tenant_matches_lead()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.lead_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM leads
          WHERE id = NEW.lead_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: lead_products.lead_id % belongs to a different tenant than lead_products.tenant_id %',
              NEW.lead_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_lead_product_tenant_matches_lead ON public.lead_products;
CREATE TRIGGER trg_lead_product_tenant_matches_lead
    BEFORE INSERT OR UPDATE OF lead_id, tenant_id
    ON public.lead_products
    FOR EACH ROW EXECUTE FUNCTION check_lead_product_tenant_matches_lead();

-- ── lead_products → products ──────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_lead_product_tenant_matches_product()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.product_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM products
          WHERE id = NEW.product_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: lead_products.product_id % belongs to a different tenant than lead_products.tenant_id %',
              NEW.product_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_lead_product_tenant_matches_product ON public.lead_products;
CREATE TRIGGER trg_lead_product_tenant_matches_product
    BEFORE INSERT OR UPDATE OF product_id, tenant_id
    ON public.lead_products
    FOR EACH ROW EXECUTE FUNCTION check_lead_product_tenant_matches_product();

-- ── product_inventories → products ────────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_inventory_tenant_matches_product()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.product_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM products
          WHERE id = NEW.product_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: product_inventories.product_id % belongs to a different tenant than product_inventories.tenant_id %',
              NEW.product_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_inventory_tenant_matches_product ON public.product_inventories;
CREATE TRIGGER trg_inventory_tenant_matches_product
    BEFORE INSERT OR UPDATE OF product_id, tenant_id
    ON public.product_inventories
    FOR EACH ROW EXECUTE FUNCTION check_inventory_tenant_matches_product();

-- ── product_inventories → warehouses ─────────────────────────────────────────
CREATE OR REPLACE FUNCTION check_inventory_tenant_matches_warehouse()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.warehouse_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM warehouses
          WHERE id = NEW.warehouse_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: product_inventories.warehouse_id % belongs to a different tenant than product_inventories.tenant_id %',
              NEW.warehouse_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_inventory_tenant_matches_warehouse ON public.product_inventories;
CREATE TRIGGER trg_inventory_tenant_matches_warehouse
    BEFORE INSERT OR UPDATE OF warehouse_id, tenant_id
    ON public.product_inventories
    FOR EACH ROW EXECUTE FUNCTION check_inventory_tenant_matches_warehouse();

-- ── product_inventories → warehouse_locations ─────────────────────────────────
CREATE OR REPLACE FUNCTION check_inventory_tenant_matches_location()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.warehouse_location_id IS NOT NULL AND NEW.tenant_id IS NOT NULL THEN
        PERFORM 1 FROM warehouse_locations
          WHERE id = NEW.warehouse_location_id AND tenant_id = NEW.tenant_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION
              'cross-tenant FK violation: product_inventories.warehouse_location_id % belongs to a different tenant than product_inventories.tenant_id %',
              NEW.warehouse_location_id, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_inventory_tenant_matches_location ON public.product_inventories;
CREATE TRIGGER trg_inventory_tenant_matches_location
    BEFORE INSERT OR UPDATE OF warehouse_location_id, tenant_id
    ON public.product_inventories
    FOR EACH ROW EXECUTE FUNCTION check_inventory_tenant_matches_location();
