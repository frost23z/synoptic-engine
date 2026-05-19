-- Phase 2 / Sprint 2d: performance indexes called for in
-- analysis/03-cross-company-sharing.md § 13 and analysis/05-database-design.md
-- "Indexes worth thinking about".
--
-- Tuned for the hot paths once cross-tenant sharing is in play:
--   - resource_visibility lookups by consumer + type are the RLS hot path
--     (already covered by V037's idx_rv_lookup).
--   - lead listing under sharing wants tenant_id + status + user_id sorted by created_at.
--   - cross_tenant_audit query patterns (already covered by V038).

CREATE INDEX IF NOT EXISTS idx_leads_tenant_status_user_created
    ON leads (tenant_id, status, user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- The cross-tenant audit owner/resource index in V038 sorts by `at DESC`, but for
-- pagination over a fixed (resource_type, resource_id) we benefit from a partial
-- index that excludes huge VIEW-only history. Skip for now — measure first.

-- record_shares lookup by owner is heavy when the owner lists "everything I've shared":
CREATE INDEX IF NOT EXISTS idx_record_shares_owner_active
    ON record_shares (owner_tenant_id, created_at DESC)
    WHERE revoked_at IS NULL;
