# Implementation Roadmap

> Phases are ordered so each phase produces a system that's safer than the one before it. Skipping phases (especially Phase 0) creates compounding correctness problems that are hard to back out of later.

T-shirt sizes: XS (<2h), S (2-4h), M (4-8h), L (1-2d), XL (2-4d).

---

## Phase 0 — Tenant foundation (~1 week)

The premise of every other phase. Source: `07-verification-findings.md` P0 items.

### P0.1 — JWT carries `tenantId` + filter sets `TenantContext` (M)

**Files**

- `auth/config/JwtTokenProvider.kt` — add `tenantId` to claims.
- `auth/config/JwtAuthFilter.kt` — extract claim, call `TenantContext.set(tenantId)`; clear in a `finally` (the existing `TenantFilterInterceptor.afterCompletion` clears, but the auth filter runs earlier; either move clear to filter `try/finally` or rely on a single owner).
- `identity/UserCredentials` / `identity/IdentityApi` — include `tenantId`.
- `identity/service/UserService.toCredentials()` — populate it.
- `auth/service/AuthService.buildTokenResponse()` — pass it.
- `identity/domain/User.kt` — add `var tenantId: UUID` exposed property (column already exists since V019).
- `shared/domain/BaseEntity.kt:51-54` — change `@PrePersist` to **throw** if `TenantContext.get() == null`, not silently default.

**Test:** Two integration tests — two tenants, two users, each can only see their own data. Login response embeds tenant_id; JWT decodes it.

### P0.2 — Composite unique constraints (S)

Migration `V026__per_tenant_unique_constraints.sql` (see `05-database-design.md`). Update JPA annotations on `User`, `Role`, `Group`, `Tag`, `LeadType`, `LeadSource`, `EmailTemplate`, `MarketingEvent`, `MarketingCampaign` to use `@Table(uniqueConstraints = [...])` instead of `@Column(unique = true)`.

**Test:** Two tenants both create a role named "ADMIN" without conflict.

### P0.3 — `Tenant` entity + `TenantProvisioningService` (M)

**Files**

- New `identity/domain/Tenant.kt`, `identity/repo/TenantRepository.kt`.
- New `identity/service/TenantProvisioningService.kt`:
  ```kotlin
  @Transactional
  fun provision(name: String, slug: String, adminEmail: String, adminPassword: String): Tenant {
      val tenant = tenantRepository.save(Tenant().apply { ... })
      TenantContext.set(tenant.id!!)
      try {
          seedDefaultRoles()
          seedDefaultPipeline()
          seedDefaultLeadSources()
          seedDefaultLeadTypes()
          seedAdminUser(adminEmail, adminPassword)
      } finally {
          TenantContext.clear()
      }
      return tenant
  }
  ```
- New `identity/web/TenantController.kt` — `POST /api/admin/tenants` gated on a hard-coded `super-admin` permission (or a config-driven master key for the first install).
- `bootstrap/BootstrapService.kt` — keep it for the seed tenant only; remove the global role creation that doesn't know about tenants.

**Test:** Provision a second tenant, log in as its admin, see empty data + default pipeline + roles populated.

### P0.4 — `Role.permissionType` (ALL / CUSTOM) (S)

Migration `V027__role_permission_type.sql`. Entity adds `permissionType: RoleType`. `UserService.toCredentials()` returns `["*"]` for any role with `ALL`. `WildcardPermissionEvaluator` handles the match (see `04-permission-model.md` § 5).

**Test:** Create a new permission key after a tenant is provisioned; an ADMIN role retroactively has access without manual grant.

### P0.5 — Permission keys reconciled (M)

Per `04-permission-model.md`. Concrete steps:

- Update every `*PermissionRegistry.kt` to publish the keys listed in §4 of the permission doc.
- Update every controller's `@PreAuthorize` annotation per §10.
- Migration `V034__permission_keys_alignment.sql` inserts the new keys and back-fills `.create` for CUSTOM roles that already had `.edit`.
- Update `BootstrapService.upsertDefaultRoles()` to use proper keys (it references `users.delete` literally today).
- Update frontend `usePermissions.ts` and any hard-coded permission strings in `.vue` pages.

### P0.6 — Postgres RLS (L) — _optional in Phase 0, mandatory before Phase 2_

Migration `V028__rls_per_tenant.sql`. Add `app.current_tenant` setter to a JDBC connection lifecycle (`HikariDataSource` proxy or a `@Transactional` AOP advice that runs `SET LOCAL` after `BEGIN`).

Two DB roles: `synoptic_app` (RLS-bound) and `synoptic_owner` (bypass, for Flyway + `BootstrapService`).

Defer this only if it can be batched with Phase 2 work — but **never** ship a tenant onboarding pipeline without RLS active.

---

## Phase 1 — Correctness fixes (~1.5 weeks)

Drawn from `02-crm-gaps-to-implement.md` § P1.

### Sprint 1a — Activities, persons, pipelines

- **P1.1 Activity type additions (S)** — `V029`, additive enum, NOTE auto-done, schedule-nullable validation. (`02 § 1.1`)
- **P1.2 Activity location + additional (S)** — `V029` covers it. (`02 § 1.2`)
- **P1.3 Activity participants person support (M)** — `V030`, new `ActivityParticipant` entity, two new endpoints. (`02 § 1.3`)
- **P1.4 Person JSON contacts (M)** — `V031` + Jackson converter, DTOs, services. (`02 § 1.4`)
- **P1.5 Pipeline `rottenDays` + guards (M)** — `V032`, delete guards on `PipelineService` and `StageService`. (`02 § 1.5`)
- **P1.6 Quote `personId` + addresses (M)** — `V032`. (`02 § 1.6`)

### Sprint 1b — Scoping, permissions, business rules

- **P1.7 `ScopeResolver` + close scoping leaks (L)** — extract helper, fix `LeadService.search/kanban`, `ActivityService`, `EmailService`, `QuoteService.search`. Add tests per service. (`02 § 1.7`)
- **P1.8 `*.create` permissions wired through controllers (M)** — registries, annotations, frontend mapping. (`02 § 1.8`)
- **P1.9 Business rule guards (M)** — UserService self-deactivation, last-admin guard, RoleService usage check, PersonService lead check, MarketingEventService campaign check. (`02 § 1.9`)
- **P1.10 Soft-delete extensions + `@SQLDelete`/`@Where` (L)** — `V033`, annotations on every soft-deletable entity. (`02 § 3.1`, `3.2`)

### Sprint 1c — Polish + tests

- **P1.11 Duplicate `UserPrincipal` removal (XS)** — `02 § 3.3`.
- **P1.12 Multi-tenant integration tests (M)** — add cross-tenant assertions to every existing integration test class.
- **P1.13 `ModularityTests` strengthening (S)** — assert CRM doesn't import inventory internals.

---

## Phase 2 — Cross-company sharing (~4 weeks)

Source: `03-cross-company-sharing.md` § 15. Restated here with sizing.

### Sprint 2a — Relationships + policies (1 week)

- **P2.1 Tenant relationship schema + entity (M)** — `V035`, `TenantRelationship.kt`, `TenantRelationshipController` with handshake (request → accept → revoke). No sharing yet.
- **P2.2 Share policy schema + admin CRUD (M)** — `V036`, `TenantSharePolicy.kt`, controller; data only, no enforcement.
- **P2.3 Frontend admin pages (L)** — Nuxt pages under `/settings/relationships` and `/settings/share-policies`.

### Sprint 2b — Visibility + query rewriting (1 week)

- **P2.4 `resource_visibility` table + triggers + policy materialization queue (L)** — `V038`, `V040`, `MaterializationWorker.kt`.
- **P2.5 RLS extension to include shared (M)** — `V041` updates each policy to add the `OR EXISTS` clause.
- **P2.6 Hibernate filter update (M)** — extend `@Filter` condition per `03 § 5.2`.
- **P2.7 Service-layer audit logging (M)** — append to `cross_tenant_audit` on cross-tenant edits.

### Sprint 2c — Record-level shares + cascades (1 week)

- **P2.8 `record_shares` endpoints + UI (M)** — `V037`, share/unshare endpoints, UI affordance in record detail pages.
- **P2.9 Cascade rules (M)** — implementing the table in `03 § 7`, with cascade row insertion in triggers.
- **P2.10 Notification integration (M)** — emit ApplicationEvents, persist to `notifications` table, UI badge.

### Sprint 2d — Hardening + perf (1 week)

- **P2.11 Conflict + revocation tests (M)** — concurrent edits, share revocation while consumer is editing, expired shares.
- **P2.12 Load test + indexing pass (M)** — see `05-database-design.md` § "Indexes worth thinking about".
- **P2.13 Documentation + admin runbook (S)** — how to onboard a new relationship, how to debug a "I can't see the shared lead" support call.

---

## Phase 3 — Dashboard, workflow, remaining parity (~2 weeks)

- **P3.1 Dashboard stats endpoints (L)** — eight stat types per `02 § 2.5`. Add the supporting indexes (`05 § "Indexes"`).
- **P3.2 Workflow action engine (L)** — strategy registry, the seven Krayin action types per `02 § 2.10`. Condition evaluator with `AND`/`OR` of `(attribute, operator, value)`.
- **P3.3 Mail drafts, forward, per-folder permissions (M)** — `02 § 2.7`.
- **P3.4 Quote lead-product sync + search + expired filter (M)** — `02 § 2.3`, `2.4`.
- **P3.5 Web form public submission (M)** — `02 § 2.6`. Already allow-listed in `SecurityConfig`.
- **P3.6 Activity calendar + meeting overlap (M)** — `02 § 2.8`.
- **P3.7 Mail compose attachments (M)** — `02 § 2.9`.

---

## Phase 4 — ERP foundation modules (open-ended)

Not in scope to size here. The architecture is in place: each new module declares its `*Api` port, its `*PermissionRegistry`, its entities (extending `BaseEntity`/`AuditableEntity`), and ships migrations that add `tenant_id`, `version`, `deleted_at`, and (where applicable) RLS policies.

Suggested module order:

1. **Procurement** — purchase orders against existing products + warehouses. Reuses Quote-like UX with reversed parties.
2. **Inventory operations** — receipts, transfers, adjustments on top of existing `product_inventories`.
3. **HR lite** — employees as a specialised `Person` with payroll + leave. Reuses Person entity with a `role: EMPLOYEE` discriminator or a separate table linked by `person_id`.
4. **Basic accounting** — invoices (from quotes + procurement), payments, simple ledger. Couples to all three modules above.

Cross-company sharing extends naturally — share a product catalog with a distributor, share a purchase order between buyer and supplier, etc.

---

## Cross-cutting work (continuous)

- **Test coverage budget**: every new endpoint adds an integration test, with at least one cross-tenant assertion after Phase 0 lands.
- **Permission drift check**: a Spring Modulith bean iterates every `@PreAuthorize` annotation and asserts the key exists in the permission catalog. Fails the boot if not. Already partly implemented by `IdentityPermissionRegistryTest`; extend.
- **Migration drift check**: a Flyway integration test that uses `pg_dump` schema diff between fresh-from-V001 and dump-of-prod. Catches entity changes without migrations and vice versa.
- **Tenant context discipline**: a Spring `RequestContext`-style assertion that every transactional method has a tenant set (or has explicit `@SystemTenant` annotation for bootstrap paths).

---

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Phase 0 done sloppily; some endpoint forgets to set tenant | M | Critical | Linter rule: every `@RestController` request hits the JWT auth filter; that filter must set tenant or 401. Add a startup assertion that the `TenantFilterInterceptor` and `JwtAuthFilter` are both in the chain. |
| RLS policies and Hibernate filters diverge | M | Critical | Single source of truth — generate one from the other. A test that runs each entity query both with and without RLS enabled and compares results. |
| `resource_visibility` table grows unbounded | M | High | Partitioning by `consumer_tenant_id` + retention job for revoked-and-expired rows. Add metrics. |
| Cross-tenant audit log balloons | L | Medium | Partition by month; cold storage after 12 months. |
| Permission registry / migration / role bootstrap go out of sync | H | Medium | The boot-time drift check above. |
| Custom-attribute schema differences confuse shared-record consumers | M | Medium | Phase 2 ships read-only attribute visibility; attribute mapping is a Phase 3+ feature. |
| Concurrent edit conflicts annoy users | M | Low | `@Version` raises `OptimisticLockException`; UI catches and offers "view changes" merge prompt. |
