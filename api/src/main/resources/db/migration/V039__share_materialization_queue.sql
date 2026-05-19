-- Phase 2 / Sprint 2b: queue for the policy materializer worker.
-- When a tenant_share_policies row is inserted/updated/deleted, a trigger drops a row
-- here. A Spring @Scheduled worker drains it and populates / clears resource_visibility
-- in chunked transactions so that a policy matching 1M leads doesn't tie up the request
-- that created it.

CREATE TABLE share_materialization_queue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id           UUID NOT NULL,
    op                  VARCHAR(20) NOT NULL CHECK (op IN ('INSERT', 'UPDATE', 'DELETE', 'REVOKE')),
    enqueued_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    error               TEXT
);

CREATE INDEX idx_smq_pending ON share_materialization_queue (enqueued_at) WHERE finished_at IS NULL;
