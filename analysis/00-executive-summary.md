# Synoptic Engine — Executive Summary

## What We're Building

A next-generation ERP/CRM platform that starts with full Krayin CRM parity and is built on a multi-tenant architecture from day one. The unique differentiator is **first-class, permission-controlled cross-company resource sharing** (parent ↔ subsidiary, partner ↔ partner, supplier → client). The CRM is the foundation; inventory, procurement, HR, and accounting modules are layered on top through module ports.

## Why this beats Krayin on its own

The single biggest customer problem we solve:

> Organizations with a parent + subsidiary structure — or any two companies that need to collaborate — have no clean way to share CRM/ERP data today. Their options are: merge tenants (no isolation), duplicate data (stale, error-prone), grant full access (security risk), or use brittle third-party integrations. None of these scale.

Our answer: a controlled sharing layer where two tenants negotiate **what** is shared (resource type), **how** (read / comment / write), and **at what granularity** (policy-level or individual records). See `03-cross-company-sharing.md`.

### Architectural deltas vs. Krayin

| Concern | Krayin | Synoptic Engine |
|---|---|---|
| Tenancy | Single-tenant per deployment | Multi-tenant from day 1 (tenant_id everywhere, RLS in Phase 0) |
| Auth | Session-based, PHP | JWT stateless, API-first |
| Primary keys | Integer auto-increment | UUID (globally unique, partitionable, safe for sharing) |
| Roles per user | 1 (`role_id` FK) | N (many-to-many `user_roles`) |
| Permission types | `all` (super-admin) / `custom` | Same — _**to be added in Phase 0**, not yet implemented_ |
| Permission scoping | Hierarchical (parent grants children) | Same — _expanded via `expandAuthorities()` in `UserService`_ |
| View scoping | `global / group / individual` | Same + `ALL` (currently identical to GLOBAL; collapse in Phase 0) |
| Concurrency | None | Optimistic locking (`@Version`) on all entities |
| Soft delete | Mostly hard deletes | Soft delete on core entities (extend coverage in Phase 0) |
| Module isolation | Laravel packages, loose | Spring Modulith ports (`CrmApi`, `IdentityApi`, `InventoryApi`, `SettingsApi`) |
| Cross-company | Out of scope (each install is one company) | Built into the platform |

---

## Current state (verified May 2026)

> **Read `07-verification-findings.md` first.** The original analysis (which described an intended architecture) is overoptimistic about what's actually implemented. The summary below has been corrected against a line-by-line audit.

### What's actually implemented

- CRM REST API covering most Krayin entities and operations (see `01-krayin-parity-status.md`). ~80% of Krayin's surface area by endpoint count.
- Spring Boot 4 / Kotlin 2.3 / Java 25, Flyway migrations up to `V024`.
- JWT auth with access + refresh tokens, password reset flow, soft delete on a subset of entities.
- Spring Modulith with module ports (`CrmApi`, `IdentityApi`, `InventoryApi`, `SettingsApi`) and event publishing tables.
- Hibernate `@Filter(name = "tenantFilter")` declared on all domain entities and `tenant_id` columns on all tables.
- Nuxt 4 / Pinia frontend with 45 pages, full CRUD UIs for the implemented modules.
- Integration tests for auth, leads, contacts, activities, quotes, pipelines, tags.

### What needs work — graded

**P0 (must fix before adding any feature):**
- The JWT does **not** carry `tenant_id` and `TenantContext` is never `set()` from the auth filter. Every request runs as the seed tenant. (`07-verification-findings.md` § P0-1)
- Several `unique = true` constraints are global where they should be per-tenant. (§ P0-2)
- No mechanism to provision a new tenant with default pipelines, roles, lead sources. (§ P0-3)
- No `Role.permissionType` (`ALL` / `CUSTOM`) — adding new permissions silently locks them out for existing roles. (§ P0-4)
- Permission keys diverge between code (`users.delete`), analysis (`settings.users.delete`), and Krayin (`settings.user.users.delete`). Pick one and align. (§ P0-5)
- `Tenant` exists only as a DB table; no JPA entity, no provisioning service. (§ P0-6)

**P1 (correctness, before broad feature work):**
- `LeadService.search()` skips view scoping → permission leak. Other services miss scoping too. (§ P1-1)
- `LeadController.create` uses `leads.edit`; no `leads.create`. (§ P1-2)
- `ActivityType` enum partially correct; the analysis recommendation to wipe & replace would lose data. (§ P1-3)
- `Email.folders` is already multi-value JSONB; don't "fix" it. (§ P1-4)
- Soft delete is manual everywhere; no `@SQLDelete` / `@Where`. Easy to leak. (§ P1-5)
- Bootstrap admin lands in seed tenant — must move into the provisioning flow. (§ P1-6)

**P2 (design risk, before scaling):**
- Cross-company sharing design (analysis 03) was truncated and lacks the query/write/audit plumbing. Now rewritten. (§ P2-1, see `03-cross-company-sharing.md`)
- `@Filter` is JPA-only and bypassable by native queries; recommend Postgres RLS as the authoritative boundary. (§ P2-2)
- `Quote` is missing `personId`, addresses, and proper status flow. (§ P2-3)

**P3 (hygiene):**
- Three different entity base classes (`BaseEntity` / `AuditableEntity` / hand-rolled `Permission`). Add a `GlobalCatalogEntity` superclass.
- Two `UserPrincipal` classes coexist; delete one.
- CRM occasionally imports `inventory.product.repo.*` directly, bypassing the module port.

---

## Revised next steps (priority order)

1. **Phase 0 — Tenant foundation (~1 week)** — fix multi-tenant plumbing, composite unique constraints, role permission-type, tenant provisioning service, permission-key reconciliation, `Tenant` entity. See `06-implementation-roadmap.md` Phase 0.
2. **Phase 1 — Correctness fixes (~1.5 weeks)** — scoping leaks, `leads.create`, `ActivityType` additive migration, `Person.emails`/`contact_numbers`, `Pipeline.rottenDays` + guards, `Quote.personId` + addresses, soft-delete coverage. See `02-crm-gaps-to-implement.md`.
3. **Phase 2 — Cross-company sharing (~4 weeks)** — only after Phase 0 is real. Builds `tenant_relationships`, `share_policies`, `record_shares`, `resource_visibility_index`, plus the query-rewriting layer (Hibernate filter + Postgres RLS). See the rewritten `03-cross-company-sharing.md`.
4. **Phase 3 — Dashboard, workflow actions, remaining gaps (~2 weeks)** — Krayin-parity dashboard stats, automation action types beyond `LOG`, remaining mass operations.
5. **Phase 4 — ERP foundation modules** — procurement, HR, basic accounting, all built on the same multi-tenant + sharing primitives.

The big change from the original roadmap: **Phase 0 is new and non-optional.** Building cross-company sharing on top of a broken multi-tenant boundary would have to be undone.
