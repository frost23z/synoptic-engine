-- Phase 1b / P1.8: split `.create` out from `.edit`.
-- Today POST endpoints are gated on `.edit`, which conflates "create new" with
-- "update existing". Add a separate `.create` per resource and back-fill it onto
-- every CUSTOM role that already has `.edit` so existing tenants keep working.
-- ADMIN roles are `permission_type = ALL` and pick up new keys automatically.

INSERT INTO permissions (id, key, description)
VALUES (gen_random_uuid(), 'users.create',      'Create users'),
       (gen_random_uuid(), 'roles.create',      'Create roles'),
       (gen_random_uuid(), 'groups.create',     'Create groups'),
       (gen_random_uuid(), 'leads.create',      'Create leads'),
       (gen_random_uuid(), 'contacts.create',   'Create contacts'),
       (gen_random_uuid(), 'activities.create', 'Create activities'),
       (gen_random_uuid(), 'quotes.create',     'Create quotes'),
       (gen_random_uuid(), 'tags.create',       'Create tags'),
       (gen_random_uuid(), 'pipelines.create',  'Create pipelines'),
       (gen_random_uuid(), 'products.create',   'Create products'),
       (gen_random_uuid(), 'warehouses.create', 'Create warehouses'),
       (gen_random_uuid(), 'attributes.create', 'Create custom attributes'),
       (gen_random_uuid(), 'automations.create','Create automations'),
       (gen_random_uuid(), 'marketing.create',  'Create marketing campaigns and events'),
       (gen_random_uuid(), 'imports.create',    'Create data imports')
ON CONFLICT (key) DO NOTHING;

-- For every CUSTOM role that already has <ns>.edit, grant <ns>.create too.
-- Idempotent: the WHERE NOT EXISTS clause means re-running the migration is safe.
WITH pairs (edit_key, create_key) AS (
    VALUES ('users.edit',       'users.create'),
           ('roles.edit',       'roles.create'),
           ('groups.edit',      'groups.create'),
           ('leads.edit',       'leads.create'),
           ('contacts.edit',    'contacts.create'),
           ('activities.edit',  'activities.create'),
           ('quotes.edit',      'quotes.create'),
           ('tags.edit',        'tags.create'),
           ('pipelines.edit',   'pipelines.create'),
           ('products.edit',    'products.create'),
           ('warehouses.edit',  'warehouses.create'),
           ('attributes.edit',  'attributes.create'),
           ('automations.edit', 'automations.create'),
           ('marketing.edit',   'marketing.create'),
           ('imports.edit',     'imports.create')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_new.id
FROM   role_permissions rp
JOIN   permissions p_old ON p_old.id = rp.permission_id
JOIN   pairs                ON pairs.edit_key  = p_old.key
JOIN   permissions p_new ON p_new.key = pairs.create_key
WHERE  NOT EXISTS (
           SELECT 1
           FROM   role_permissions rp_existing
           WHERE  rp_existing.role_id       = rp.role_id
             AND  rp_existing.permission_id = p_new.id
       );
