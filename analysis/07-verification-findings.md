# Verification Findings — Audit of Current Codebase vs. Existing Analysis

> Written after a full read of `api/src/main/kotlin/**` and a cross-check against the original analysis docs and Krayin reference.
>
> Every finding cites file paths and line numbers so it can be re-verified independently.

This document is **the most important pre-implementation read**. The existing analysis docs (00-06) described an intended architecture; in several places that architecture is _not actually wired up_ in code, or is wired up in a way that will break the moment a second tenant exists. Cross-company sharing cannot be built on the current foundation without fixing these first.

Findings are graded:

- **P0** — Blocks the next phase. Fix before adding any feature.
- **P1** — Correctness bug. Visible to users sooner or later.
- **P2** — Design risk. Will hurt during scale or future modules.
- **P3** — Hygiene / cleanup.

---

## P0-1 — Multi-tenancy is not actually applied per request

**Claim in `00-executive-summary.md`:** "Multi-tenant architecture (tenant isolation on all domain tables)."

**Reality in code:** The tenant filter parameter is **always** the seed tenant ID. Every authenticated request runs as `00000000-0000-0000-0000-000000000001`.

**Evidence:**

- `api/src/main/kotlin/com/synopticengine/api/shared/TenantContext.kt:5-17` — `ThreadLocal<UUID?>`; `getOrDefault()` falls back to `SEED_TENANT_ID`.
- `api/src/main/kotlin/com/synopticengine/api/shared/config/TenantFilterInterceptor.kt:21` — `TenantContext.getOrDefault()` is used to set the Hibernate filter.
- `api/src/main/kotlin/com/synopticengine/api/auth/config/JwtAuthFilter.kt` — extracts userId/email/authorities; **never calls `TenantContext.set(...)`**.
- `api/src/main/kotlin/com/synopticengine/api/auth/config/JwtTokenProvider.kt:21-31` — the JWT contains `email`, `authorities`, `type`. **No `tenantId` claim.**
- `grep -rn 'TenantContext\.set' src/main/` returns no matches.

**Consequences:**

1. The `@Filter(name = "tenantFilter")` on every entity reads/writes only seed-tenant rows.
2. `BaseEntity.@PrePersist` (`BaseEntity.kt:51-54`) assigns `tenantId = SEED_TENANT_ID` to every new record.
3. If a second tenant is ever provisioned, its users will silently see and edit the first tenant's data.
4. Cross-company sharing is meaningless until tenant identity per request is correct.

**Fix outline (see `05-database-design.md` and `06-implementation-roadmap.md` P0 sprint):**

1. Add `tenantId: UUID` to `User` entity + persist column (already exists in DB via V019).
2. Embed `tenantId` claim in the JWT (`JwtTokenProvider.generateAccessToken`).
3. In `JwtAuthFilter`, after building the principal, call `TenantContext.set(tenantId)`.
4. Make `BaseEntity.@PrePersist` throw if `TenantContext.get()` returns null (no silent fallback for new rows).
5. Add an integration test: two users in two tenants must not see each other's records.

---

## P0-2 — Unique constraints are global, but data is per-tenant

Entities below have `unique = true` on a column that should be unique **per tenant**, not globally:

| Entity | File | Field | Should be |
|---|---|---|---|
| `User` | `identity/domain/User.kt:22` | `email` | unique `(tenant_id, email)` |
| `Role` | `identity/domain/Role.kt:17` | `name` | unique `(tenant_id, name)` |
| `Group` | `identity/domain/Group.kt:13` | `name` | unique `(tenant_id, name)` |
| `Tag` | `crm/tag/domain/Tag.kt:13` | `name` | unique `(tenant_id, name)` |
| `LeadType` | `crm/lead/domain/LeadType.kt:13` | `name` | unique `(tenant_id, name)` |
| `LeadSource` | `crm/lead/domain/LeadSource.kt:13` | `name` | unique `(tenant_id, name)` |

`Permission.key` (`identity/domain/Permission.kt:26`) is correctly globally unique — permissions are a system catalog, not per-tenant.

**Consequences if not fixed:** Two tenants cannot both have a role named "ADMIN", a tag named "VIP", or even a user with the same email. The first tenant's data wins; the second tenant's writes fail.

**Fix:** Drop the simple unique constraint, add a composite unique on `(tenant_id, <field>)` in a new migration. Update entity annotations to `@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["tenant_id", "<field>"])])`.

---

## P0-3 — No per-tenant seed of pipelines / roles / permissions / system defaults

`BootstrapService.kt:26-34` runs once on `ApplicationReadyEvent` and seeds the seed tenant. There is **no mechanism** to seed defaults (default pipeline, default stages, default roles, default lead sources) when a **new** tenant is created.

A new tenant created via API today would have:

- No roles → users cannot be assigned any role → no one can log in
- No default pipeline → leads cannot be created (FK to default pipeline ID `…010`)
- No default stages → kanban breaks

**Fix:** Build a `TenantProvisioningService.provision(name, adminEmail, adminPassword)` that, inside a transaction with `TenantContext.set(newTenantId)`:

1. Inserts default pipeline + stages
2. Inserts default lead sources and types
3. Inserts default roles (ADMIN/MANAGER/SALESPERSON/VIEWER) wired to the global permission catalog
4. Inserts the initial admin user with the ADMIN role

Expose this from a controlled endpoint (initially admin-only) and call it from the existing bootstrap path so dev/test still has data.

---

## P0-4 — `Role` lacks a `permission_type` (all / custom) bypass

In Krayin (`krayin-features-analysis/15-permissions.md`), a role with `permission_type = 'all'` bypasses all permission checks (super-admin). With ~75 permission keys, an ADMIN role currently lists all of them — every new permission added must be backfilled to ADMIN or admins lose access. `Permission` keys are also tenant-global, but role-permission assignments live per tenant: if a tenant's ADMIN role was created before a new permission was added, that tenant's ADMIN no longer has the new permission.

**Evidence:** `Role.kt` has only a `permissions: MutableSet<Permission>` relation (line 23). `BootstrapService.upsertRole("ADMIN", …, permissionNames = allPermissionNames)` (lines 51-55) just snapshots the current set.

**Fix:**

- Add `Role.permissionType: RoleType` enum (`ALL`, `CUSTOM`).
- `UserCredentials.toCredentials()` (`UserService.kt:187-200`) returns `["*"]` for ALL roles.
- `@PreAuthorize("hasAuthority('leads.view') or hasAuthority('*')")` — or wrap into a Spring Security `PermissionEvaluator` that handles the wildcard centrally.
- Bootstrap ADMIN as `permissionType = ALL` and stop snapshotting permission lists.

---

## P0-5 — Permission key tree is inconsistent across code, analysis, and Krayin

Three different naming systems are in play right now:

| Source | Example keys |
|---|---|
| **Code** (`identity/IdentityPermissions.kt`, `crm/CrmPermissions.kt`, etc.) | `users.view`, `users.edit`, `users.delete`, `leads.view`, `leads.edit`, `leads.delete` (no `.create`), `mail.view`, `mail.edit` (no per-folder, no compose) |
| **Krayin** (`krayin-features-analysis/15-permissions.md`) | `settings.user.users.create`, `leads.create`, `mail.compose`, `mail.inbox` |
| **Analysis 04** (`04-permission-model.md`) | `settings.users.view`, `leads.create`, `contacts.persons.view` |

Bootstrap filters reference keys that don't exist:

- `BootstrapService.kt:62` excludes `"users.delete"` and `"roles.edit"` — only works because the current naming happens to match. If keys are renamed to `settings.user.users.delete` (Krayin-style), MANAGER silently gains those rights.
- `SALESPERSON` uses `key.startsWith("tags")` (`BootstrapService.kt:75`), which matches `tags.*` but would NOT match Krayin's `settings.other_settings.tags.*`.

**Decision required before P1 work begins.** See `04-permission-model.md` for the recommended approach: keep the **flat** keys (current code) plus add `.create` as a sibling of `.edit`. Don't adopt Krayin's deeply-nested `settings.user.users.delete` style — it adds noise without buying anything when permissions can be expanded via the parent prefix.

---

## P0-6 — `Tenant` exists in DB only, not in code

`V018__create_tenants.sql` creates the `tenants` table with `id`, `name`, `slug`, but there is no `Tenant` JPA entity, no `TenantRepository`, no `TenantController`. New tenants cannot be created without raw SQL.

**Fix:** Add `Tenant` entity + repository + `TenantProvisioningController` (admin-only) that calls the service from P0-3.

---

## P1-1 — Service scoping is partial and duplicated

`resolveScope()` is copy-pasted into:

- `crm/lead/service/LeadService.kt:75-78`
- `crm/contact/service/PersonService.kt:36-39`
- `crm/contact/service/OrganizationService.kt:30-33`
- `crm/quote/service/QuoteService.kt:50-53`

Each looks up the user by email on every call, which is one extra DB query per scoped action. Also:

- **`LeadService.search()`** (`LeadService.kt:53-57`) does **not** apply `resolveScope()` — a salesperson with `INDIVIDUAL` view can search and see all leads. **Permission leak.**
- `Activity`, `Email`, `Product`, `Warehouse` services don't apply view scoping at all. Krayin scopes activities and emails via the lead they belong to.

**Fix:**

1. Move scoping into a single helper / `@Component ScopeResolver` injected where needed.
2. Cache `ViewContext` per request (extract from JWT or stash in `SecurityContextHolder`).
3. Audit every list/search/filter method and add scoping where appropriate (with a unit test per service).
4. `ViewPermission.ALL` and `ViewPermission.GLOBAL` are treated identically in `UserService.resolveViewContext()` (`UserService.kt:158-160`) — collapse to one or document the intended difference.

---

## P1-2 — `leads.edit` is currently the create permission too

`LeadController.create()` is gated on `leads.edit` (`LeadController.kt:72`). Krayin and the analysis both have a separate `leads.create`. Same pattern likely applies to `contacts.persons.create`, `contacts.organizations.create`, `activities.create`, `quotes.create`, `products.create` — verify per controller.

**Fix:** add `leads.create`, `contacts.persons.create`, etc., to the permission registries. Update controller annotations. ADMIN/MANAGER roles inherit via wildcard or via the SALESPERSON allowlist update.

---

## P1-3 — `ActivityType` enum is partially wrong; analysis recommendation would lose data

`crm/activity/domain/ActivityType.kt` currently has `CALL, EMAIL, MEETING, TASK, NOTE, MESSAGE`.
Krayin has `call, meeting, lunch, note, file`.
Analysis 02 recommends `enum class ActivityType { CALL, MEETING, LUNCH, NOTE, FILE }` — this would lose any existing `EMAIL`/`TASK`/`MESSAGE` rows.

**Fix:** Additive migration — add `LUNCH` and `FILE`, deprecate `TASK` (UI no longer offers it; DB still accepts), keep `EMAIL`/`MESSAGE` for backwards compatibility or back-fill to `CALL`/`NOTE` if business confirms.

---

## P1-4 — `Email.folders` is already multi-value JSONB; analysis treats it as single

`crm/email/domain/Email.kt` stores `folders` as JSON (matching Krayin: an email can be in `inbox` and `tagged-important` simultaneously). The analysis description in `02-crm-gaps-to-implement.md` implicitly assumes a single folder. Don't "fix" this — it's already correct. Re-verify endpoints in the email controller treat the input as a list.

---

## P1-5 — Soft delete is manual on every query, not enforced at entity level

Only 9 of 30+ entities implement `SoftDeletable`. None use Hibernate's `@SQLDelete` or `@Where(clause = "deleted_at IS NULL")`. Every repository must remember to add `findActiveById`, `findAllByDeletedAtIsNull`, etc. A future repository method that forgets the filter leaks deleted rows.

**Fix (P1 if you want safety, P3 if velocity matters more):** Add `@SQLDelete(sql = "UPDATE … SET deleted_at = NOW() WHERE id = ? AND version = ?")` and `@Where(clause = "deleted_at IS NULL")` to every soft-deletable entity. Watch for the gotcha: `@Where` is bypassed by native queries, so leak audits still matter.

Also: `Email`, `EmailAttachment`, `WebForm`, `Workflow`, `MarketingCampaign`, `EmailTemplate`, `Attribute` should probably be soft-deletable (they're referenced by other records).

---

## P1-6 — Bootstrap admin lands in seed tenant, not the tenant they should administer

`BootstrapService.upsertAdminUser()` (`BootstrapService.kt:94-106`) is called outside any `TenantContext.set(...)`, so the admin is created with `tenant_id = SEED_TENANT_ID`. After fixing P0-1, the bootstrap path also needs to know which tenant the admin belongs to — recommend that bootstrap only seeds the **seed** tenant and that all new tenants go through the provisioning service from P0-3.

---

## P2-1 — Cross-company sharing design (analysis 03) is incomplete

`03-cross-company-sharing.md` was truncated at line 101 and stops before describing how shared records are actually fetched. The schema (`tenant_relationships`, `tenant_share_policies`, `resource_shares`) is reasonable but missing:

- Query rewriting layer (how does `LeadRepository.findAll()` see shared records?)
- Write semantics (audit, version conflict, ownership, who can re-share)
- Cascade rules (sharing a Lead — does it implicitly share its Person? Activities? Quote?)
- Conflict resolution and notification
- Revocation semantics
- Performance plan for the visibility lookup

`03-cross-company-sharing.md` has been rewritten to cover all of these.

---

## P2-2 — Hibernate `@Filter` is not safe enough to be the only tenant boundary

`@Filter` is enforced only on entities loaded through the JPA `Session`. It does **not** apply to:

- Native queries (e.g. `@Query(nativeQuery = true)` in repositories)
- Raw JDBC queries
- Reports written via `EntityManager.createNativeQuery(...)`
- Any future read-only replica access that bypasses Hibernate

**Recommendation:** Use **Postgres Row-Level Security (RLS)** as the authoritative boundary, with `@Filter` kept as a secondary optimization. The DB session sets `SET LOCAL app.current_tenant = :tenantId` per transaction; RLS policies reference it. This is also the cleanest way to extend the boundary to "own tenant OR shared with me" for cross-org sharing (see updated 03).

---

## P2-3 — `Quote` is detached from the Lead-Person relationship

`crm/quote/domain/Quote.kt` is referenced by an explicit `leadId` (the Krayin migration column) but has no `personId`. Krayin's quote model has `person_id NOT NULL` and `user_id NOT NULL`. Analysis 02 mentions adding `personId` (good). Also missing: `billing_address` / `shipping_address` as JSONB, `expiredAt`, status flow.

---

## P3-1 — `Permission` entity is hand-rolled, missing `BaseEntity` benefits

`identity/domain/Permission.kt` does not extend `BaseEntity`, so it has its own `id`, `version`, audit timestamps. This is intentional (Permission is a global catalog, not per-tenant), but means three different entity base layouts coexist (`BaseEntity`, `AuditableEntity`, hand-rolled). Add a `GlobalCatalogEntity` mapped superclass that covers the no-tenant case cleanly, or accept the duplication.

---

## P3-2 — Duplicate `UserPrincipal` classes

`api/src/main/kotlin/com/synopticengine/api/auth/UserPrincipal.kt` and `api/src/main/kotlin/com/synopticengine/api/auth/config/UserPrincipal.kt` both exist. Confirm which is used and delete the other.

---

## P3-3 — Spring Modulith module ports exist but boundaries leak in places

`CrmApi`, `IdentityApi`, `InventoryApi`, `SettingsApi` are defined, but services in CRM import from `inventory.product.repo.*` directly (e.g. `QuoteService` likely reads `ProductRepository`). Either expose those reads through `InventoryApi` or accept the leak as pragmatic for now and document it.

---

## What this changes for the roadmap

The original roadmap (`06-implementation-roadmap.md`) jumps straight into closing CRM parity gaps. Reorder to:

1. **Phase 0 — Tenant Foundation** (P0-1, P0-2, P0-3, P0-4, P0-5, P0-6 — ~1 week)
2. **Phase 1 — Correctness fixes** (P1-1 .. P1-6 — ~1.5 weeks)
3. **Phase 2 — Cross-company sharing** (now built on a sound multi-tenant base — ~4 weeks)
4. **Phase 3 — Remaining parity gaps + dashboard + workflow actions** (~2 weeks)
5. **Phase 4 — ERP modules**

Skipping Phase 0 will manifest as silent data leaks between tenants the first time someone provisions a second tenant. Phase 0 also unblocks meaningful test coverage of multi-tenant scenarios.
