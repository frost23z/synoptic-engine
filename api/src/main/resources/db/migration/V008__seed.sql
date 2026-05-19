-- V008__seed.sql
-- Baseline seed: default tenant, default CRM lookups, default pipeline + stages,
-- application-managed permission keys, and the system_configs catalog.
--
-- Notes:
--  * The admin user is created at boot by BootstrapService (not seeded here).
--  * Role assignments are also bootstrapped at runtime — only the permission
--    catalog itself lives in SQL so the FK constraint targets exist when the
--    application starts.

-- Default tenant
INSERT INTO tenants (id, name, slug)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Tenant', 'default');

-- Default pipeline + stages
INSERT INTO pipelines (id, name, description, is_active, is_default, rotten_days)
VALUES ('00000000-0000-0000-0000-000000000010', 'Default Pipeline', 'Standard sales pipeline', TRUE, TRUE, 30);

INSERT INTO stages (id, pipeline_id, name, sort_order, probability, code)
VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000010', 'New',         1,  10, NULL),
       ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000010', 'Qualified',   2,  30, NULL),
       ('00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000010', 'Proposal',    3,  60, NULL),
       ('00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000010', 'Negotiation', 4,  80, NULL),
       ('00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000010', 'Won',         5, 100, 'won'),
       ('00000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000010', 'Lost',        6,   0, 'lost');

-- Default lead sources
INSERT INTO lead_sources (id, name)
VALUES (gen_random_uuid(), 'Website'),
       (gen_random_uuid(), 'Referral'),
       (gen_random_uuid(), 'Cold Outreach'),
       (gen_random_uuid(), 'Social Media'),
       (gen_random_uuid(), 'Event');

-- Default lead types
INSERT INTO lead_types (id, name)
VALUES (gen_random_uuid(), 'Inbound'),
       (gen_random_uuid(), 'Outbound'),
       (gen_random_uuid(), 'Partner');

-- Permission catalogue (full set after Phase 1b + Phase 2 sharing).
INSERT INTO permissions (id, key, description)
VALUES (gen_random_uuid(), 'users.create',           'Create users'),
       (gen_random_uuid(), 'roles.create',           'Create roles'),
       (gen_random_uuid(), 'groups.create',          'Create groups'),
       (gen_random_uuid(), 'leads.create',           'Create leads'),
       (gen_random_uuid(), 'contacts.create',        'Create contacts'),
       (gen_random_uuid(), 'activities.create',      'Create activities'),
       (gen_random_uuid(), 'quotes.create',          'Create quotes'),
       (gen_random_uuid(), 'tags.create',            'Create tags'),
       (gen_random_uuid(), 'pipelines.create',       'Create pipelines'),
       (gen_random_uuid(), 'products.create',        'Create products'),
       (gen_random_uuid(), 'warehouses.create',      'Create warehouses'),
       (gen_random_uuid(), 'attributes.create',      'Create custom attributes'),
       (gen_random_uuid(), 'automations.create',     'Create automations'),
       (gen_random_uuid(), 'marketing.create',       'Create marketing campaigns and events'),
       (gen_random_uuid(), 'imports.create',         'Create data imports'),
       (gen_random_uuid(), 'relationships',          'Manage tenant relationships'),
       (gen_random_uuid(), 'relationships.view',     'View tenant relationships'),
       (gen_random_uuid(), 'relationships.manage',   'Initiate, accept, and revoke relationships'),
       (gen_random_uuid(), 'share-policies',         'Manage share policies'),
       (gen_random_uuid(), 'share-policies.view',    'View share policies'),
       (gen_random_uuid(), 'share-policies.manage',  'Create and revoke share policies'),
       (gen_random_uuid(), 'records',                'Manage cross-tenant record actions'),
       (gen_random_uuid(), 'records.share',          'Share an individual record with another tenant'),
       (gen_random_uuid(), 'records.reshare',        'Reshare a record received via share (MANAGE only)');

-- System configuration catalogue (codes only — values are populated by tenants).
INSERT INTO system_configs (code, label, group_name, type, is_secret, sort_order) VALUES
    ('general.company_name',    'Company Name',    'general', 'text',     FALSE, 10),
    ('general.company_email',   'Company Email',   'general', 'email',    FALSE, 20),
    ('general.company_phone',   'Company Phone',   'general', 'text',     FALSE, 30),
    ('general.company_address', 'Company Address', 'general', 'textarea', FALSE, 40),
    ('general.locale',          'Default Locale',  'general', 'text',     FALSE, 50),
    ('general.timezone',        'Timezone',        'general', 'text',     FALSE, 60),
    ('mail.driver',             'Mail Driver',     'mail',    'text',     FALSE, 10),
    ('mail.host',               'SMTP Host',       'mail',    'text',     FALSE, 20),
    ('mail.port',               'SMTP Port',       'mail',    'text',     FALSE, 30),
    ('mail.username',           'SMTP Username',   'mail',    'text',     FALSE, 40),
    ('mail.password',           'SMTP Password',   'mail',    'password', TRUE,  50),
    ('mail.encryption',         'Encryption',      'mail',    'text',     FALSE, 60),
    ('mail.from_name',          'From Name',       'mail',    'text',     FALSE, 70),
    ('mail.from_address',       'From Address',    'mail',    'email',    FALSE, 80);
