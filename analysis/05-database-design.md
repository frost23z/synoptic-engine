# Database Design — Migrations Roadmap

## Current state (V024)

24 migrations applied. All tables have:

- UUID primary keys (`gen_random_uuid()`)
- `tenant_id UUID NOT NULL REFERENCES tenants(id)` since `V019`
- `version BIGINT NOT NULL DEFAULT 0` for optimistic locking since `V020`
- `deleted_at TIMESTAMPTZ` on the 9 entities that implement `SoftDeletable`
- A single global default tenant (`00000000-0000-0000-0000-000000000001`)

The `tenants` table itself is bare (`id`, `name`, `slug`, `created_at`). No status, no parent reference, no soft delete.

---

## Phase 0 — Tenant foundation migrations

These are P0 from `07-verification-findings.md`. Do them before any other DB changes.

### `V025__tenant_metadata_and_provisioning.sql`

Bring `tenants` up to what the application needs.

```sql
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS status      VARCHAR(20)  NOT NULL DEFAULT 'active' CHECK (status IN ('active','suspended','revoked')),
    ADD COLUMN IF NOT EXISTS legal_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS timezone    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS locale      VARCHAR(16),
    ADD COLUMN IF NOT EXISTS deleted_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version     BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants (status) WHERE deleted_at IS NULL;
```

### `V026__per_tenant_unique_constraints.sql`

Replace global unique constraints with composite per-tenant ones. (P0-2)

```sql
-- Users
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE users ADD CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email);

-- Roles
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_key;
ALTER TABLE roles ADD CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name);

-- Groups
ALTER TABLE groups DROP CONSTRAINT IF EXISTS groups_name_key;
ALTER TABLE groups ADD CONSTRAINT uq_groups_tenant_name UNIQUE (tenant_id, name);

-- Tags
ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_name_key;
ALTER TABLE tags ADD CONSTRAINT uq_tags_tenant_name UNIQUE (tenant_id, name);

-- Lead types / sources
ALTER TABLE lead_types DROP CONSTRAINT IF EXISTS lead_types_name_key;
ALTER TABLE lead_types ADD CONSTRAINT uq_lead_types_tenant_name UNIQUE (tenant_id, name);
ALTER TABLE lead_sources DROP CONSTRAINT IF EXISTS lead_sources_name_key;
ALTER TABLE lead_sources ADD CONSTRAINT uq_lead_sources_tenant_name UNIQUE (tenant_id, name);

-- Email templates, marketing events/campaigns also have unique = true; same treatment.
ALTER TABLE email_templates DROP CONSTRAINT IF EXISTS email_templates_name_key;
ALTER TABLE email_templates ADD CONSTRAINT uq_email_templates_tenant_name UNIQUE (tenant_id, name);
ALTER TABLE marketing_events DROP CONSTRAINT IF EXISTS marketing_events_name_key;
ALTER TABLE marketing_events ADD CONSTRAINT uq_marketing_events_tenant_name UNIQUE (tenant_id, name);
ALTER TABLE marketing_campaigns DROP CONSTRAINT IF EXISTS marketing_campaigns_name_key;
ALTER TABLE marketing_campaigns ADD CONSTRAINT uq_marketing_campaigns_tenant_name UNIQUE (tenant_id, name);
```

Coupled JPA change — replace `@Column(unique = true)` with `@Table(uniqueConstraints = [...])` on each entity.

### `V027__role_permission_type.sql`

Adds the `ALL`/`CUSTOM` super-admin bypass. (P0-4)

```sql
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS permission_type VARCHAR(10) NOT NULL DEFAULT 'CUSTOM'
        CHECK (permission_type IN ('ALL','CUSTOM'));

-- Mark every existing ADMIN role across all tenants as ALL.
UPDATE roles SET permission_type = 'ALL' WHERE name = 'ADMIN';
```

### `V028__rls_per_tenant.sql`

Postgres Row-Level Security as the authoritative tenant boundary. (P2-2)

```sql
-- One example; repeat for every tenant-scoped table.
ALTER TABLE leads ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_select ON leads
    FOR SELECT USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY tenant_isolation_modify ON leads
    FOR ALL USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- A separate admin role that bypasses RLS, used for migrations and the BootstrapService.
-- The application connects as the regular role for normal requests.
```

Apply to: `users`, `roles`, `groups`, `tags`, `leads`, `lead_products`, `lead_sources`, `lead_types`, `organizations`, `persons`, `pipelines`, `stages`, `activities`, `activity_files`, `activity_participants`, `quotes`, `quote_items`, `emails`, `email_attachments`, `email_tags`, `products`, `product_inventories`, `warehouses`, `warehouse_locations`, `attributes`, `attribute_options`, `attribute_values`, `email_templates`, `web_forms`, `web_form_attributes`, `data_imports`, `system_configs`, `workflows`, `webhooks`, `marketing_campaigns`, `marketing_events`, `datagrid_saved_filters`, `user_password_resets`.

Operationally:

- Connect as `synoptic_app` (RLS-bound role) for normal API requests.
- Connect as `synoptic_owner` (RLS-bypassing) for Flyway and `BootstrapService`.
- Wrap every `@Transactional` method in an AOP advice that runs `SET LOCAL app.current_tenant = '<uuid>'` after the connection is acquired.

If RLS feels too heavy for early MVP, defer this migration to Phase 2 (cross-org sharing), keeping just the Hibernate filter. **Caveat:** any future native query bypasses Hibernate, so RLS is the only durable enforcement.

---

## Phase 1 — CRM correctness migrations

### `V029__activity_type_additions.sql`

```sql
ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_type_check;
ALTER TABLE activities ADD CONSTRAINT activities_type_check
    CHECK (type IN ('CALL','MEETING','LUNCH','NOTE','FILE','TASK','EMAIL','MESSAGE'));

ALTER TABLE activities ADD COLUMN IF NOT EXISTS location   VARCHAR(255);
ALTER TABLE activities ADD COLUMN IF NOT EXISTS additional JSONB;
ALTER TABLE activities ALTER COLUMN schedule_from DROP NOT NULL;
ALTER TABLE activities ALTER COLUMN schedule_to   DROP NOT NULL;
```

### `V030__activity_participants_revamp.sql`

```sql
ALTER TABLE activity_participants RENAME COLUMN user_id TO participant_user_id;
ALTER TABLE activity_participants ALTER COLUMN participant_user_id DROP NOT NULL;
ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS id        UUID PRIMARY KEY DEFAULT gen_random_uuid();
ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS person_id UUID REFERENCES persons(id) ON DELETE CASCADE;
ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES tenants(id);
ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS version   BIGINT NOT NULL DEFAULT 0;
ALTER TABLE activity_participants ADD CONSTRAINT chk_participant_one
    CHECK (
        (participant_user_id IS NOT NULL AND person_id IS NULL) OR
        (participant_user_id IS NULL AND person_id IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS idx_activity_participants_activity ON activity_participants (activity_id);
```

### `V031__person_json_contacts.sql`

```sql
ALTER TABLE persons ADD COLUMN IF NOT EXISTS emails          JSONB NOT NULL DEFAULT '[]';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS contact_numbers JSONB NOT NULL DEFAULT '[]';

UPDATE persons SET emails = jsonb_build_array(jsonb_build_object('value', email, 'label', 'primary'))
    WHERE email IS NOT NULL AND emails = '[]'::jsonb;

UPDATE persons SET contact_numbers = jsonb_build_array(jsonb_build_object('value', phone, 'label', 'primary'))
    WHERE phone IS NOT NULL AND contact_numbers = '[]'::jsonb;

-- Drop old columns later, after read path cuts over (e.g. V0NN+2):
-- ALTER TABLE persons DROP COLUMN email;
-- ALTER TABLE persons DROP COLUMN phone;
```

### `V032__pipeline_and_quote_additions.sql`

```sql
ALTER TABLE pipelines ADD COLUMN IF NOT EXISTS rotten_days INT;

ALTER TABLE quotes ADD COLUMN IF NOT EXISTS person_id        UUID REFERENCES persons(id) ON DELETE SET NULL;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS billing_address  JSONB;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS shipping_address JSONB;

-- Backfill person_id from linked lead where available.
UPDATE quotes q
SET person_id = l.person_id
FROM leads l
WHERE q.lead_id = l.id
  AND q.person_id IS NULL
  AND l.person_id IS NOT NULL;
```

### `V033__soft_delete_extensions.sql`

Add `deleted_at` (and `@SQLDelete` on the entity side) to entities that should be soft-deletable.

```sql
ALTER TABLE emails              ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE email_attachments   ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE email_templates     ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE web_forms           ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE workflows           ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE webhooks            ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE marketing_campaigns ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE marketing_events    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE attributes          ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_emails_not_deleted ON emails (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_email_templates_not_deleted ON email_templates (id) WHERE deleted_at IS NULL;
```

### `V034__permission_keys_alignment.sql`

Backfill new permission keys per `04-permission-model.md` § 9. Idempotent.

```sql
INSERT INTO permissions (id, key, description) VALUES
    (gen_random_uuid(), 'dashboard',                 'View dashboard'),
    (gen_random_uuid(), 'leads.create',              'Create lead'),
    (gen_random_uuid(), 'contacts.persons.create',   'Create person'),
    (gen_random_uuid(), 'contacts.persons.view',     'View person'),
    (gen_random_uuid(), 'contacts.persons.edit',     'Edit person'),
    (gen_random_uuid(), 'contacts.persons.delete',   'Delete person'),
    -- ... contacts.organizations.*, activities.create, quotes.create/print/mail,
    --     mail.compose/inbox/draft/outbox/sent/trash/delete, products.create,
    --     warehouses.create, pipelines.create/delete, tags.create, etc.
    -- Add the cross-tenant keys here too if Phase 2 follows immediately.
ON CONFLICT (key) DO NOTHING;

-- Backfill existing CUSTOM roles: if they already have .edit, add .create.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_new.id
FROM role_permissions rp
JOIN permissions p_old ON p_old.id = rp.permission_id AND p_old.key = 'leads.edit'
JOIN permissions p_new ON p_new.key = 'leads.create'
LEFT JOIN role_permissions rp_existing ON rp_existing.role_id = rp.role_id AND rp_existing.permission_id = p_new.id
WHERE rp_existing.role_id IS NULL;

-- Repeat for every (.edit → .create) pair.
```

---

## Phase 2 — Cross-company sharing migrations

See `03-cross-company-sharing.md` § 4 for the full DDL. Summary:

- `V035__tenant_relationships.sql`
- `V036__tenant_share_policies.sql`
- `V037__record_shares.sql`
- `V038__resource_visibility.sql` + indexes + triggers
- `V039__cross_tenant_audit.sql`
- `V040__share_materialization_queue.sql`
- `V041__rls_extend_for_sharing.sql` (update each RLS policy to include `OR EXISTS (SELECT 1 FROM resource_visibility …)`)

---

## Phase 3 — Dashboard, automation, polish

- `V042__dashboard_index_supports.sql` — composite indexes that the dashboard queries need (`leads (tenant_id, status, created_at)`, `lead_products (tenant_id, product_id, lead_id)` for top-product summaries, etc.). Add only after measuring query plans.
- `V043__workflow_actions_table.sql` — if action execution starts logging, add `workflow_action_runs(workflow_id, action_index, status, payload, error, ran_at)`.
- `V044__notifications.sql` — `notifications(id, tenant_id, user_id, kind, payload, read_at, created_at)` for both cross-tenant share notifications and in-tenant workflow notices.

---

## Migration discipline

- **Never** drop a column in the same migration that introduces its replacement. Cut over reads first, then ship a separate migration to drop.
- **Never** add a NOT NULL column without a DEFAULT or a backfill in the same migration.
- **Always** make every constraint addition `IF NOT EXISTS` and every `DROP` `IF EXISTS` — partial-success migrations should be replayable.
- **RLS migrations** are append-only: enabling RLS without policies locks the table to admin only. Always include the policy in the same migration that enables RLS.
- **Trigger functions** in V038 / V041 should be `CREATE OR REPLACE` so re-running the migration during dev is safe.

## Indexes worth thinking about

After Phase 0 and 1, run a load test and `EXPLAIN ANALYZE` on the top-10 endpoints. Likely additions:

- `leads (tenant_id, status, user_id, created_at DESC)` — kanban + listing
- `activities (tenant_id, schedule_from, is_done)` — calendar + dashboard
- `emails (tenant_id, folders, created_at DESC)` — once `folders` JSONB indexing is needed, use a GIN index on `folders` and a per-folder partial index for the hot ones.
- `resource_visibility (consumer_tenant_id, resource_type, expires_at)` — share lookups

Don't add these speculatively; measure first.
