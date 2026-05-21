-- V012__system_configs_per_tenant_pk.sql
--
-- H9 (codebase audit) — make `system_configs` truly per-tenant.
--
-- Background:
--   V005 created `system_configs` with `code` as the primary key plus a
--   `tenant_id` column (defaulting to the seed tenant). The JPA entity
--   modelled only `code`, so the in-app behaviour was "single global
--   catalog row per code, value shared across every tenant" — incompatible
--   with the multi-tenant goal: any tenant updating mail.host would
--   silently rewrite it for every other tenant on the platform.
--
-- This migration changes the primary key to `(tenant_id, code)` so each
-- tenant has its own copy of the catalog. The JPA entity is updated in the
-- same commit to model the composite key; `TenantProvisioningService`
-- listeners will populate the catalog for each new tenant from the seed
-- tenant's rows.

ALTER TABLE public.system_configs
    DROP CONSTRAINT pk_system_configs;

ALTER TABLE public.system_configs
    ADD CONSTRAINT pk_system_configs PRIMARY KEY (tenant_id, code);

-- The existing seed-tenant rows (V008) keep the same `code` values but now
-- live under tenant_id = SEED. No data migration needed; the PK change is
-- a no-op for the data already on disk.

CREATE INDEX IF NOT EXISTS idx_system_configs_tenant_group
    ON public.system_configs (tenant_id, group_name, sort_order);
