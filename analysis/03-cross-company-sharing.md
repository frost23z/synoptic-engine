# Cross-Company Resource Sharing — Design

> **Pre-requisite:** Phase 0 of the roadmap (real multi-tenant boundary, RLS, tenant provisioning) must be complete before this design can be built. The query rewriting in §5 assumes that the auth layer reliably puts a single tenant identity into the request context.

## 1. The problem

> Organizations with a parent + subsidiary structure, or any two companies that need to collaborate, have no clean way to share CRM/ERP data. Their current options — merge tenants, manual export, third-party integrations, full-access invitations — all fail at scale.

We give them a fourth option: **structured, permission-controlled, real-time resource sharing** at the platform level, with two tenants that remain fully isolated everywhere else.

## 2. Use cases that drive the design

1. **Parent → Subsidiary** — Holding company shares all its leads in a region with the regional subsidiary (read-only, via policy). Subsidiary works the leads, edits the activities. Parent sees updates in real time.
2. **Subsidiary → Parent** — Subsidiary shares pipeline + revenue stats with parent for consolidated reporting (read-only, policy-level, aggregations only).
3. **Partner ↔ Partner** — Two independent companies collaborating on a single deal. Each shares one lead (ad-hoc, write-level) with the other so both teams can coordinate without copying data.
4. **Supplier → Client** — Manufacturer shares its product catalog and price list (policy-level, read-only) with each distributor tenant. Distributors quote against the live catalog. Supplier can revoke at any time.
5. **Audit reversal** — A share is revoked. The downstream tenant immediately loses visibility, but their prior activities/comments remain in the audit log so the upstream tenant can see what happened.

These five cases cover the spectrum of: who initiates, who controls revocation, whether data flows one way or two, and whether the sharing is template-based or per-record.

## 3. Core concepts

### 3.1 Relationship

A **directed edge** between two tenants. Symmetric relationships (PARTNER) are modelled as two edges. The directionality matters because access policies and revocation rights are per-direction.

| Type | Meaning | Revocation rights |
|---|---|---|
| `PARENT_CHILD` | Owner tenant is structurally above the dependent | Parent can revoke at will; child cannot revoke parent |
| `PARTNER` | Peer relationship; bidirectional grant negotiated | Either side may revoke their own grant |
| `SUPPLIER_CLIENT` | Source pushes catalog data to consumer | Supplier may revoke at will; client may opt out |

A tenant can be in multiple relationships simultaneously and can play different roles in each.

### 3.2 Shareable resource type

A fixed enum of resource families. Adding a type is a code change (new policy mapping, new visibility column).

```
contacts.persons
contacts.organizations
products
products.pricelists
leads
leads.activities
quotes
warehouses
```

Activities tied to a shared lead are sub-resources — sharing a lead does **not** automatically share its activity timeline unless the policy says so.

### 3.3 Access level

Per resource type, per direction:

| Level | Read | Comment | Mutate | Delete | Reshare |
|---|:-:|:-:|:-:|:-:|:-:|
| `NONE` | | | | | |
| `READ` | ✓ | | | | |
| `COMMENT` | ✓ | ✓ | | | |
| `WRITE` | ✓ | ✓ | ✓ | | |
| `MANAGE` | ✓ | ✓ | ✓ | ✓ | ✓ |

`COMMENT` lets a consumer attach activities and notes without modifying the owner's record. This is the common case for sales collaboration.

### 3.4 Two layers of sharing

| Layer | Configured by | Granularity | Example |
|---|---|---|---|
| **Policy** | Tenant admins | All records of a resource type matching an optional filter | "Share all leads in pipeline `Enterprise` with subsidiary X, READ" |
| **Record share** | Any user with `share` permission on the record | A single record | "Share this one lead with partner Y, WRITE, expires in 60 days" |

Both layers feed the same visibility index (§ 5.2). Per-record shares always override / extend policy — they cannot reduce a grant that's already higher.

## 4. Data model

### 4.1 Tables

```sql
-- A tenant relationship is one directed edge; PARTNER creates two rows.
CREATE TABLE tenant_relationships (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    relationship_type   VARCHAR(30) NOT NULL CHECK (
        relationship_type IN ('PARENT_CHILD', 'PARTNER', 'SUPPLIER_CLIENT')
    ),
    status              VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (
        status IN ('pending', 'active', 'suspended', 'revoked')
    ),
    initiated_by        UUID NOT NULL REFERENCES users(id),
    accepted_by         UUID REFERENCES users(id),
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at         TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenant_relationship UNIQUE (source_tenant_id, target_tenant_id, relationship_type),
    CONSTRAINT chk_distinct_tenants CHECK (source_tenant_id <> target_tenant_id)
);
CREATE INDEX idx_tr_source ON tenant_relationships (source_tenant_id, status);
CREATE INDEX idx_tr_target ON tenant_relationships (target_tenant_id, status);

-- Policy = "share all of resource X with this tenant at this access level"
CREATE TABLE tenant_share_policies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    relationship_id     UUID NOT NULL REFERENCES tenant_relationships(id) ON DELETE CASCADE,
    resource_type       VARCHAR(50) NOT NULL,
    access_level        VARCHAR(20) NOT NULL CHECK (
        access_level IN ('NONE', 'READ', 'COMMENT', 'WRITE', 'MANAGE')
    ),
    filter_jsonb        JSONB,                              -- optional: only share records matching this filter
    cascade_jsonb       JSONB,                              -- optional: which related resources implicitly share
    created_by          UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_share_policy UNIQUE (relationship_id, resource_type)
);
CREATE INDEX idx_tsp_relationship ON tenant_share_policies (relationship_id) WHERE revoked_at IS NULL;

-- Ad-hoc per-record share
CREATE TABLE record_shares (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_tenant_id     UUID NOT NULL REFERENCES tenants(id),
    consumer_tenant_id  UUID NOT NULL REFERENCES tenants(id),
    resource_type       VARCHAR(50) NOT NULL,
    resource_id         UUID NOT NULL,
    access_level        VARCHAR(20) NOT NULL,
    shared_by           UUID NOT NULL REFERENCES users(id),
    expires_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_record_share UNIQUE (owner_tenant_id, consumer_tenant_id, resource_type, resource_id)
);
CREATE INDEX idx_rs_lookup ON record_shares (consumer_tenant_id, resource_type, resource_id)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_rs_owner ON record_shares (owner_tenant_id, resource_type, resource_id)
    WHERE revoked_at IS NULL;

-- Materialized visibility index — query hot path. Refreshed by triggers / app code.
-- One row per (consumer, resource) tuple. Populated by both policies and record_shares.
CREATE TABLE resource_visibility (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_tenant_id     UUID NOT NULL,
    consumer_tenant_id  UUID NOT NULL,
    resource_type       VARCHAR(50) NOT NULL,
    resource_id         UUID NOT NULL,
    access_level        VARCHAR(20) NOT NULL,
    source              VARCHAR(20) NOT NULL CHECK (source IN ('policy', 'record', 'cascade')),
    source_id           UUID NOT NULL,                      -- policy id or record_share id or parent resource id
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_visibility UNIQUE (consumer_tenant_id, resource_type, resource_id, source, source_id)
);
CREATE INDEX idx_rv_lookup ON resource_visibility (consumer_tenant_id, resource_type, resource_id);
CREATE INDEX idx_rv_owner ON resource_visibility (owner_tenant_id, resource_type, resource_id);

-- Cross-tenant audit log. Records every action where the actor's tenant differs from the record's owner tenant.
CREATE TABLE cross_tenant_audit (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_tenant_id     UUID NOT NULL,
    actor_tenant_id     UUID NOT NULL,
    actor_user_id       UUID NOT NULL,
    resource_type       VARCHAR(50) NOT NULL,
    resource_id         UUID NOT NULL,
    action              VARCHAR(40) NOT NULL,               -- VIEW / EDIT / COMMENT / DELETE / SHARE / RESHARE / REVOKE
    payload_jsonb       JSONB,                              -- field-level diff for EDITs
    at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cta_owner_resource ON cross_tenant_audit (owner_tenant_id, resource_type, resource_id, at DESC);
CREATE INDEX idx_cta_actor ON cross_tenant_audit (actor_tenant_id, at DESC);
```

### 4.2 Why a separate `resource_visibility` table?

The alternative is to compute visibility on every query (joining policies + record_shares + relationship status). That works at low scale but degrades sharply once a policy covers a million leads. The materialized table:

- Has O(1) lookup per row.
- Is updated by triggers when policies / shares / underlying records change (see § 7).
- Is rebuildable from `tenant_share_policies` + `record_shares` if it gets out of sync.

The cost is the trigger plumbing. For policies that match a huge volume of records we can fall back to a **virtual visibility** strategy (don't materialize, evaluate at query time using `EXISTS (SELECT 1 FROM tenant_share_policies WHERE …)`) — see § 5.3.

## 5. Query rewriting — how shared records are read

This is the section that was missing from the previous draft. There are three places where the boundary must enforce "own + shared":

### 5.1 Postgres Row-Level Security (authoritative)

Per § P2-2 in `07-verification-findings.md`, the trustworthy boundary is RLS, not Hibernate filters. Each tenant-scoped table gets a policy:

```sql
ALTER TABLE leads ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_own_or_shared ON leads
    USING (
        tenant_id = current_setting('app.current_tenant')::uuid
        OR EXISTS (
            SELECT 1 FROM resource_visibility v
            WHERE v.consumer_tenant_id = current_setting('app.current_tenant')::uuid
              AND v.resource_type = 'leads'
              AND v.resource_id = leads.id
              AND (v.expires_at IS NULL OR v.expires_at > NOW())
        )
    );

-- Write policy is stricter: tenant_id must match OR a WRITE/MANAGE visibility exists.
CREATE POLICY tenant_write_check ON leads
    FOR UPDATE USING (
        tenant_id = current_setting('app.current_tenant')::uuid
        OR EXISTS (
            SELECT 1 FROM resource_visibility v
            WHERE v.consumer_tenant_id = current_setting('app.current_tenant')::uuid
              AND v.resource_type = 'leads'
              AND v.resource_id = leads.id
              AND v.access_level IN ('WRITE', 'MANAGE')
              AND (v.expires_at IS NULL OR v.expires_at > NOW())
        )
    );
```

The application connection sets `SET LOCAL app.current_tenant = :tenantId` at the start of every transaction (in a `@Transactional` AOP advice or `DataSource` interceptor). RLS makes accidental cross-tenant reads impossible — even via native queries or future read-replica access. This addresses § P2-2 of the verification findings.

### 5.2 Hibernate `@Filter` (defense in depth + ORM laziness)

Keep the existing `@Filter(name = "tenantFilter")` but extend the condition:

```kotlin
@FilterDef(
    name = "tenantFilter",
    parameters = [
        ParamDef(name = "tenantId", type = UUID::class)
    ],
)
// On each shareable entity:
@Filter(
    name = "tenantFilter",
    condition = """
        tenant_id = :tenantId
        OR id IN (
            SELECT v.resource_id FROM resource_visibility v
            WHERE v.consumer_tenant_id = :tenantId
              AND v.resource_type = '<resource_type_literal>'
              AND (v.expires_at IS NULL OR v.expires_at > NOW())
        )
    """,
)
```

The literal resource type is per-entity, so we either generate the filter per entity (acceptable — ~10 of them) or pass it as a second filter parameter. Hibernate filters can take multiple parameters.

The Hibernate filter exists for two reasons even though RLS is the trust boundary:

1. Hibernate plan caches benefit from the filter being explicit.
2. When lazy associations load (e.g. `lead.person`), Hibernate fires a query that bypasses the application service layer but is still tenant-filtered.

### 5.3 Virtual visibility for large-volume policies

If a policy matches >100k records, materializing `resource_visibility` rows is wasteful. Add an opt-in to `tenant_share_policies`:

- `cascade_jsonb.materialize = false` → instead of trigger-populating `resource_visibility`, leave it empty and let the RLS / Hibernate filter add a second `OR EXISTS` clause that walks the policy.

This is a per-policy decision a tenant admin makes at policy creation time.

## 6. Write semantics

When a consumer tenant writes to a record they don't own:

1. **Authorization check** — `resource_visibility.access_level ∈ {WRITE, MANAGE}` for the consumer.
2. **Application-level permission check** — the consumer still needs `leads.edit` in their own role. A user with `READ_ONLY` role at consumer can never write, even if the share allows WRITE.
3. **Optimistic locking** — uses the record's `@Version` column as today. Concurrent writes from owner + consumer race normally; loser gets `OptimisticLockException`.
4. **Audit row** — a `cross_tenant_audit` row is appended with the actor's tenant + user, action, and a field-level diff in `payload_jsonb`.
5. **Owner notification** — a `cross_tenant_event` is published (Spring `ApplicationEventPublisher`) and turned into a notification in the owner tenant's notification feed.

The `BaseEntity.@PrePersist` is unchanged — `tenant_id` keeps reflecting the owning tenant. Cross-tenant edits do not change ownership.

### 6.1 Deletes by a consumer

A consumer with `MANAGE` access can soft-delete the record. The owner sees it as deleted but can restore from soft-delete. A consumer with `WRITE` cannot delete. A consumer with `READ`/`COMMENT` obviously cannot.

### 6.2 Reshare prevention

Only `MANAGE` grants reshare. The new share's owner tenant remains the original — a downstream cannot "launder" a share to widen access.

## 7. Cascade rules

When a record is shared, related records can either be implicit (cascade) or independent. Defaults:

| Shared resource | Implicit cascade | Independent |
|---|---|---|
| `leads.<id>` | `persons.<lead.person_id>` (READ) `organizations.<lead.organization_id>` (READ) | `activities`, `quotes`, `lead_products` (independent shares required) |
| `contacts.persons.<id>` | nothing | `organizations` (linked), `activities` |
| `products.<id>` | `products.pricelists.<id>` only if policy says so | `inventory` always private |
| `leads.activities.<id>` | (no cascade — granular activity share is rare) | — |
| `quotes.<id>` | `lead` (READ) `person` (READ) | `products`, `activities` |

Cascade rows are inserted into `resource_visibility` with `source = 'cascade'` and `source_id = <parent resource id>`. When the parent share is revoked, all `source = 'cascade'` rows with the matching source_id are removed.

Policies can override the default cascade via `tenant_share_policies.cascade_jsonb`:

```jsonb
{
    "persons": "READ",
    "organizations": "READ",
    "activities": "COMMENT",
    "quotes": "NONE"
}
```

## 8. Trigger plumbing for `resource_visibility`

The hot path. Triggers are the cheapest correct option; doing it in app code risks races if two pods write simultaneously.

### 8.1 On `tenant_share_policies` insert/update/delete

When a policy is added, populate `resource_visibility` for all matching records. When it's revoked, delete the `source = 'policy', source_id = <policy.id>` rows.

Because policies can match millions of records, the insert is moved to a background job:

```sql
CREATE OR REPLACE FUNCTION enqueue_policy_materialization() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO share_materialization_queue (policy_id, op, enqueued_at)
    VALUES (NEW.id, TG_OP, NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tsp_after_change
    AFTER INSERT OR UPDATE OR DELETE ON tenant_share_policies
    FOR EACH ROW EXECUTE FUNCTION enqueue_policy_materialization();
```

A Spring `@Scheduled` task drains the queue and applies the changes in chunked transactions.

### 8.2 On the underlying entity (e.g. `leads`)

When a new lead is inserted in a tenant that has active outbound policies for `leads`, the trigger inserts the corresponding `resource_visibility` rows synchronously (a single insert is cheap).

```sql
CREATE OR REPLACE FUNCTION sync_visibility_on_lead() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO resource_visibility
        (owner_tenant_id, consumer_tenant_id, resource_type, resource_id, access_level, source, source_id)
    SELECT NEW.tenant_id, p_rel.target_tenant_id, 'leads', NEW.id, p.access_level, 'policy', p.id
    FROM tenant_share_policies p
    JOIN tenant_relationships p_rel ON p_rel.id = p.relationship_id
    WHERE p_rel.source_tenant_id = NEW.tenant_id
      AND p_rel.status = 'active'
      AND p.resource_type = 'leads'
      AND p.revoked_at IS NULL
      AND (p.filter_jsonb IS NULL OR jsonb_filter_matches(p.filter_jsonb, NEW));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

`jsonb_filter_matches` is a small immutable function that interprets the policy's filter DSL.

### 8.3 On `record_shares`

Straightforward — one row in, one row out of `resource_visibility`.

## 9. Notifications

`ApplicationEventPublisher` publishes:

- `RelationshipRequested` (target tenant admin must accept)
- `RelationshipAccepted` / `RelationshipRevoked`
- `RecordShared` / `RecordShareRevoked`
- `SharedRecordEdited` (owner side, when consumer changes a record)
- `SharedRecordCommented`

A `notifications` table per tenant (already implicit in your roadmap) receives these. The UI badges them. Email is optional and configurable per user.

## 10. Permissions

Add new permission keys (see `04-permission-model.md`):

```
relationships.view              -- see this tenant's relationships
relationships.manage            -- initiate, accept, revoke relationships

share-policies.view             -- see configured policies
share-policies.manage           -- create/edit/revoke policies

records.share                   -- share an individual record
records.reshare                 -- reshare a shared record (only with MANAGE access)
```

`relationships.manage` and `share-policies.manage` are admin-level by default. `records.share` is granted to roles like SALESPERSON for the common partner-collab case.

## 11. API surface

```
GET     /api/relationships
POST    /api/relationships                  body: { targetTenantId, type, note }
PATCH   /api/relationships/{id}/accept
PATCH   /api/relationships/{id}/revoke

GET     /api/relationships/{id}/policies
POST    /api/relationships/{id}/policies    body: { resourceType, accessLevel, filterJson?, cascadeJson? }
PUT     /api/share-policies/{id}
DELETE  /api/share-policies/{id}            -- soft revoke

POST    /api/records/share                  body: { resourceType, resourceId, consumerTenantId, accessLevel, expiresAt? }
DELETE  /api/records/share/{id}
GET     /api/records/{resourceType}/{id}/shares

GET     /api/cross-tenant-audit?resourceType=leads&resourceId=...
GET     /api/cross-tenant-audit?actorTenantId=...    (admin only)
```

All endpoints honour normal `@PreAuthorize` checks plus a new `@CrossTenantAware` aspect that:

- Reads the requested resource's owner tenant.
- If owner ≠ current tenant, looks up `resource_visibility` to determine access level.
- Throws 403 if access is insufficient.
- Logs to `cross_tenant_audit` on success.

## 12. Edge cases & how they resolve

| Case | Resolution |
|---|---|
| Consumer's role has stricter permissions than the share's access level | Effective access = min(role, share). |
| Owner soft-deletes a record while consumer has WRITE access | Consumer's queries stop returning it (the row is excluded by soft-delete `@Where`). Visibility rows remain until the record is hard-deleted. |
| Owner hard-deletes a record | Cascade on `record_shares` and `resource_visibility` removes rows. Audit retains them. |
| Owner edits a record concurrently with consumer | `@Version` race; loser sees `OptimisticLockException` and retries with stale data refreshed from the server. |
| Consumer's tenant is suspended | RLS predicate evaluates `app.current_tenant` against the active tenants table; suspended tenants get an empty result. |
| Cycle: A is parent of B, B is partner of A | Allowed. Effective access on a given (resource, consumer) is the max access across all visibility rows. |
| Consumer's custom attribute set differs from owner's | Consumer sees all owner's attributes (read-only) plus any of their own that are explicitly applied via attribute mapping (separate feature, out of scope here). |
| Owner changes a record's tenant_id (re-parents to another tenant) | Disallowed by policy — `tenant_id` is `updatable = false` in `BaseEntity`. |

## 13. Performance plan

The hot path is the RLS `EXISTS (SELECT 1 FROM resource_visibility WHERE …)`. To keep it fast:

- `resource_visibility` is partitioned by `consumer_tenant_id` (Postgres declarative partitioning) once any single consumer crosses 1M rows.
- Compound index `(consumer_tenant_id, resource_type, resource_id)` is the primary access pattern.
- A second index `(owner_tenant_id, resource_type, resource_id)` supports revocation cleanup.
- For very high-volume policies (whole catalog), use the virtual visibility strategy (§ 5.3) instead of materializing.

Benchmarks to run before considering it production-ready:

- 1M leads, 10 share policies, 100k record shares, 5k tenants — list-leads p99 < 100ms.
- Policy creation that materializes 1M rows completes in < 5 minutes background, with the queue draining smoothly.

## 14. What is _not_ in scope here

- Cross-tenant search across all shared data ("find any lead any of my partners shared with me named 'Acme'") — designed as a Phase 3 add-on using a tenant-aware search index.
- Bulk export of shared data — handled by the standard export module with the visibility predicate applied.
- Negotiation UI (chat, terms acceptance) — the API supports `note` and `accept`; the UI flow is a Phase 2 deliverable but the data model is sufficient.
- Billing/metering for shared resources — design space, not implemented.

## 15. Step-by-step implementation order

1. **0.5 week** — `Tenant`, `TenantRelationship` entities + admin endpoints (no sharing yet, just relationship handshakes).
2. **0.5 week** — `tenant_share_policies` table + admin CRUD endpoints. No materialization yet; the table is just data.
3. **1 week** — `resource_visibility` table + triggers + RLS policies for `leads`, `persons`, `organizations`, `products`. Start with policy-only (no record shares).
4. **0.5 week** — Hibernate filter updates + service-layer audit logging.
5. **0.5 week** — `record_shares` table + endpoints + UI affordance ("share this lead with…").
6. **0.5 week** — Cascade rules implementation.
7. **0.5 week** — Notifications + frontend ribbon ("This record is shared by Acme Corp").
8. **Ongoing** — Add visibility for newer modules (quotes, activities) as they're touched.

Roughly 4 calendar weeks for a small team. The biggest unknown is RLS interplay with Hibernate plan caching — budget a day for that early.
