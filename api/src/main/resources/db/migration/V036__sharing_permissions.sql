-- Phase 2 / Sprint 2a: introduce the cross-tenant permission keys.
-- See analysis/04-permission-model.md § 4 and analysis/03-cross-company-sharing.md § 10.
-- ADMIN roles (permission_type = 'ALL') pick these up implicitly via the wildcard
-- evaluator. CUSTOM roles need explicit grants.

INSERT INTO permissions (id, key, description)
VALUES (gen_random_uuid(), 'relationships',          'Manage tenant relationships'),
       (gen_random_uuid(), 'relationships.view',     'View tenant relationships'),
       (gen_random_uuid(), 'relationships.manage',   'Initiate, accept, and revoke relationships'),
       (gen_random_uuid(), 'share-policies',         'Manage share policies'),
       (gen_random_uuid(), 'share-policies.view',    'View share policies'),
       (gen_random_uuid(), 'share-policies.manage',  'Create and revoke share policies'),
       (gen_random_uuid(), 'records',                'Manage cross-tenant record actions'),
       (gen_random_uuid(), 'records.share',          'Share an individual record with another tenant'),
       (gen_random_uuid(), 'records.reshare',        'Reshare a record received via share (MANAGE only)')
ON CONFLICT (key) DO NOTHING;
