-- P1-1: ShareMaterializationWorker runs on @Scheduled with no request thread, so
-- TenantContext is null and the Hibernate filter/RLS doesn't apply. The worker
-- needs the source-tenant id at task time to set up TenantContext explicitly —
-- before this column existed, it derived the tenant by joining
-- share_materialization_queue → tenant_share_policies → tenant_relationships,
-- which couldn't itself run filtered (chicken-and-egg).
--
-- The column is populated at enqueue time (always inside an authenticated
-- transaction in the source tenant). drainQueue() then wraps each task in
-- TenantContext.runAs(tenant_id) so the policy + relationship lookups, the
-- native owner-record SELECT, and the visibility upserts all run in the right
-- tenant.

ALTER TABLE share_materialization_queue
    ADD COLUMN tenant_id uuid NOT NULL;

CREATE INDEX idx_share_mat_queue_tenant
    ON share_materialization_queue (tenant_id);
