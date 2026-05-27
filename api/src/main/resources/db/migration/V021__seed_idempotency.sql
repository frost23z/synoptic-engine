-- V021__seed_idempotency.sql
-- T6.6 — Make V008 baseline seeds idempotent with ON CONFLICT guards.
--
-- Problem:
--   V008 seeded the default tenant, pipeline/stages, lead lookups, system_config
--   catalog, and permission baseline rows using plain INSERT (no ON CONFLICT).
--   Unique constraints already exist on all of these tables (added in V001/V002/V005
--   and V012), so Flyway's checksum protection is the only guard against a duplicate
--   on a replay — fine for production, but fragile in test-harness setups that reset
--   the schema between runs.
--
-- Registries are the source of truth for permissions:
--   All permission keys seeded by V008 are also registered in the per-module
--   *PermissionRegistry beans. BootstrapService.upsertPermissions() is idempotent
--   (checks existingKeys before inserting), so V008 rows are kept as-is; do NOT
--   delete them — role_permissions FK cascade would strip ADMIN's grants.
--   Going forward, new permission keys MUST be added to a PermissionRegistry bean
--   ONLY; do NOT add them to SQL migrations.
--
-- What this migration does:
--   Re-states each V008 seed as an ON CONFLICT DO NOTHING INSERT, acting as
--   a no-op on every existing DB while making a schema-only (no-data) freshdb
--   safe to boot without running the full seed script.

-- ── Default tenant ────────────────────────────────────────────────────────────
INSERT INTO tenants (id, name, slug)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Tenant', 'default')
ON CONFLICT DO NOTHING;

-- ── Default pipeline + stages ─────────────────────────────────────────────────
INSERT INTO pipelines (id, name, description, is_active, is_default, rotten_days)
VALUES ('00000000-0000-0000-0000-000000000010', 'Default Pipeline', 'Standard sales pipeline', TRUE, TRUE, 30)
ON CONFLICT DO NOTHING;

INSERT INTO stages (id, pipeline_id, name, sort_order, probability, code)
VALUES
    ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000010', 'New',         1,  10, NULL),
    ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000010', 'Qualified',   2,  30, NULL),
    ('00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000010', 'Proposal',    3,  60, NULL),
    ('00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000010', 'Negotiation', 4,  80, NULL),
    ('00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000010', 'Won',         5, 100, 'won'),
    ('00000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000010', 'Lost',        6,   0, 'lost')
ON CONFLICT DO NOTHING;

-- ── Lead sources / types (seed tenant) ───────────────────────────────────────
INSERT INTO lead_sources (name, tenant_id)
VALUES
    ('Website',       '00000000-0000-0000-0000-000000000001'),
    ('Referral',      '00000000-0000-0000-0000-000000000001'),
    ('Cold Outreach', '00000000-0000-0000-0000-000000000001'),
    ('Social Media',  '00000000-0000-0000-0000-000000000001'),
    ('Event',         '00000000-0000-0000-0000-000000000001')
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO lead_types (name, tenant_id)
VALUES
    ('Inbound',  '00000000-0000-0000-0000-000000000001'),
    ('Outbound', '00000000-0000-0000-0000-000000000001'),
    ('Partner',  '00000000-0000-0000-0000-000000000001')
ON CONFLICT (tenant_id, name) DO NOTHING;

-- ── Permission baseline (see note above — do NOT add new keys here) ───────────
INSERT INTO permissions (key, description)
VALUES
    ('users.create',         'Create users'),
    ('roles.create',         'Create roles'),
    ('groups.create',        'Create groups'),
    ('leads.create',         'Create leads'),
    ('contacts.create',      'Create contacts'),
    ('activities.create',    'Create activities'),
    ('quotes.create',        'Create quotes'),
    ('tags.create',          'Create tags'),
    ('pipelines.create',     'Create pipelines'),
    ('products.create',      'Create products'),
    ('warehouses.create',    'Create warehouses'),
    ('attributes.create',    'Create custom attributes'),
    ('automations.create',   'Create automations'),
    ('marketing.create',     'Create marketing campaigns and events'),
    ('imports.create',       'Create data imports'),
    ('relationships',        'Manage tenant relationships'),
    ('relationships.view',   'View tenant relationships'),
    ('relationships.manage', 'Initiate, accept, and revoke relationships'),
    ('share-policies',       'Manage share policies'),
    ('share-policies.view',  'View share policies'),
    ('share-policies.manage','Create and revoke share policies'),
    ('records',              'Manage cross-tenant record actions'),
    ('records.share',        'Share an individual record with another tenant'),
    ('records.reshare',      'Reshare a record received via share (MANAGE only)')
ON CONFLICT (key) DO NOTHING;

-- ── System-config catalog (seed tenant) ──────────────────────────────────────
INSERT INTO system_configs (code, label, group_name, type, is_secret, sort_order, tenant_id)
VALUES
    ('general.company_name',    'Company Name',    'general', 'text',     FALSE, 10, '00000000-0000-0000-0000-000000000001'),
    ('general.company_email',   'Company Email',   'general', 'email',    FALSE, 20, '00000000-0000-0000-0000-000000000001'),
    ('general.company_phone',   'Company Phone',   'general', 'text',     FALSE, 30, '00000000-0000-0000-0000-000000000001'),
    ('general.company_address', 'Company Address', 'general', 'textarea', FALSE, 40, '00000000-0000-0000-0000-000000000001'),
    ('general.locale',          'Default Locale',  'general', 'text',     FALSE, 50, '00000000-0000-0000-0000-000000000001'),
    ('general.timezone',        'Timezone',        'general', 'text',     FALSE, 60, '00000000-0000-0000-0000-000000000001'),
    ('mail.driver',             'Mail Driver',     'mail',    'text',     FALSE, 10, '00000000-0000-0000-0000-000000000001'),
    ('mail.host',               'SMTP Host',       'mail',    'text',     FALSE, 20, '00000000-0000-0000-0000-000000000001'),
    ('mail.port',               'SMTP Port',       'mail',    'text',     FALSE, 30, '00000000-0000-0000-0000-000000000001'),
    ('mail.username',           'SMTP Username',   'mail',    'text',     FALSE, 40, '00000000-0000-0000-0000-000000000001'),
    ('mail.password',           'SMTP Password',   'mail',    'password', TRUE,  50, '00000000-0000-0000-0000-000000000001'),
    ('mail.encryption',         'Encryption',      'mail',    'text',     FALSE, 60, '00000000-0000-0000-0000-000000000001'),
    ('mail.from_name',          'From Name',       'mail',    'text',     FALSE, 70, '00000000-0000-0000-0000-000000000001'),
    ('mail.from_address',       'From Address',    'mail',    'email',    FALSE, 80, '00000000-0000-0000-0000-000000000001')
ON CONFLICT (tenant_id, code) DO NOTHING;
