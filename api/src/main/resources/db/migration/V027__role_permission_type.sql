-- Phase 0 / P0-4: ALL / CUSTOM bypass on roles so adding new permissions doesn't
-- silently lock out existing super-admin roles.

ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS permission_type VARCHAR(10)
        NOT NULL DEFAULT 'CUSTOM'
        CHECK (permission_type IN ('ALL', 'CUSTOM'));

-- Mark every existing ADMIN role (across all tenants) as ALL so they keep working.
UPDATE roles SET permission_type = 'ALL' WHERE name = 'ADMIN';
