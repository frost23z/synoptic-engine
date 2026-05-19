# Phase 4 Findings — Independent Audit Before the Refactor Pass

> Read of `api/src/main/kotlin/**` and `api/src/test/kotlin/**` at commit `4ea24f3`
> (Phase 3 merged), cross-checked against `analysis/00-07*.md` and the original Krayin
> reference.
>
> Every finding cites file paths and line numbers so it can be re-verified independently.
> Findings are graded the same as `07-verification-findings.md`:
>
> - **P0** — Blocks the next phase. Fix before adding any feature.
> - **P1** — Correctness bug, latent leak, or false claim in the docs.
> - **P2** — Design risk that will hurt soon (next phase or first real customer).
> - **P3** — Hygiene / cleanup.

The summary up front: the codebase is in much better shape than the state captured in
`07-verification-findings.md`. Phase 0/1/1b's per-tenant constraints, tenant context wiring,
`@SQLDelete`/`@SQLRestriction` on every soft-deletable entity, `@PreAuthorize` vs registry
alignment, and `LeadService.search` scoping are all genuinely fixed. The two big things
the audit caught are **Phase 2 claims that are not actually wired up** — RLS and the
cross-tenant audit log — plus a handful of scope leaks that survived Phase 1.

Spring Modulith verification passes: `./gradlew test --tests 'ModularityTests'` completes
green (`BUILD SUCCESSFUL in 2m 4s`), the docs in `api/build/spring-modulith-docs/` render
for all eight modules.

---

## P0-1 — Postgres RLS is documented but inert at runtime

**Claim in `analysis/03-cross-company-sharing.md` and `V040__rls_per_tenant.sql`:** RLS is
the "authoritative boundary"; the app runs `SET LOCAL app.current_tenant = '<uuid>'` on
every transaction so the policy `USING` clause resolves.

**Reality in code:** No code sets the GUC. Anywhere.

**Evidence:**

- `api/src/main/resources/db/migration/V040__rls_per_tenant.sql:11-12` — the comment
  documents the intended deployment model.
- `api/src/main/resources/db/migration/V040__rls_per_tenant.sql:23-27` — defines
  `app_current_tenant()` reading `NULLIF(current_setting('app.current_tenant', true), '')::uuid`.
- `api/src/main/resources/db/migration/V040__rls_per_tenant.sql:56-74` — the SELECT
  policy on `leads`: `app_current_tenant() IS NULL OR tenant_id = app_current_tenant()
  OR app_has_visibility(...)`. **The first clause is a bypass when the GUC is unset.**
- `grep -rln 'app.current_tenant\|app_current_tenant\|SET LOCAL\|currentTenant'
  api/src/main/` returns only `V040__rls_per_tenant.sql`. There is no
  `ConnectionProvider`, `DataSource` wrapper, `@Transactional` aspect, or
  `beforeCommit`/`afterBegin` hook that issues `SET LOCAL`.
- Tests run as the Postgres superuser via Testcontainers; RLS is bypassed there
  regardless. `MultiTenantIsolationIntegrationTest` proves Hibernate `@Filter` works,
  not that RLS works.

**Consequences:**

1. Native queries (e.g. `LeadRepository.findOpenByStageNative()`,
   `ShareMaterializationWorker.materializePolicy()` at
   `api/src/main/kotlin/com/synopticengine/api/sharing/service/ShareMaterializationWorker.kt:137-143`)
   that bypass `@Filter` and rely on RLS get **no isolation at all** — they see every row.
2. Any future read-only replica access or JDBC bulk job has zero tenant boundary.
3. The cross-tenant share read path (Phase 2) is held together by Hibernate `@Filter` +
   `ResourceVisibilityService` joins, not by the two-layer defense the design promises.

**Fix outline:**

- Add a `TenantConnectionInitializer` (a `BeanPostProcessor` for the `DataSource`, or an
  AOP aspect around `@Transactional` boundaries, or — cleanest — a `Hibernate
  Interceptor`/`SessionEventListener` on `afterTransactionBegin`) that runs
  `SET LOCAL app.current_tenant = ?` from `TenantContext.get()`.
- Make `TenantContext.get() == null` a hard error in the GUC setter — don't fall back.
- An integration test that drops superuser, runs as `synoptic_app`, and asserts that a
  raw `EntityManager.createNativeQuery("SELECT * FROM leads").resultList` only returns
  current-tenant rows.

---

## P0-2 — Cross-tenant audit log is never written

**Claim in `analysis/03-cross-company-sharing.md`:** Every cross-tenant mutation
(READ/WRITE/COMMENT/DELETE/SHARE/RESHARE/REVOKE) appends a row to `cross_tenant_audit`.

**Reality in code:** The table, entity, repository, service, and API method exist. **The
method is never called** outside its own definition.

**Evidence:**

- `api/src/main/resources/db/migration/V038__cross_tenant_audit.sql` — table exists.
- `api/src/main/kotlin/com/synopticengine/api/sharing/domain/CrossTenantAudit.kt:24-60`
  — entity, enum `CrossTenantAction { VIEW, EDIT, COMMENT, DELETE, SHARE, RESHARE,
  REVOKE }`.
- `api/src/main/kotlin/com/synopticengine/api/sharing/service/CrossTenantAuditService.kt`
  — service with a `record(...)` method.
- `api/src/main/kotlin/com/synopticengine/api/sharing/SharingApi.kt:31-39` — exposes
  `recordAudit(...)` on the public port.
- `api/src/main/kotlin/com/synopticengine/api/sharing/service/SharingApiImpl.kt:20-38` —
  delegates to the service.
- `grep -rn 'recordAudit\|crossTenantAuditService\.record' api/src/main/` returns only
  the three files above. No CRM service, no controller, no listener, no JPA listener,
  no aspect ever calls it.
- `api/src/main/kotlin/com/synopticengine/api/sharing/events/SharingEventLogger.kt:80-88`
  — `@EventListener onSharedRecordEdited` logs to SLF4J only. There is no listener that
  writes to the audit table.

**Consequences:** The contractually-promised "trail of every cross-tenant action" does
not exist. If a consumer-tenant user edits a shared lead today, the only evidence is a
log line in stderr (lost on restart). For a compliance posture this is a P0; for the
current "early dev, no production users" state it's still a P0 because it's a Phase 2
deliverable that didn't ship.

**Fix outline:**

- Either: an `@Around` aspect on `@PreAuthorize` write annotations that fires when the
  actor's tenant ≠ the record's owner tenant.
- Or: a JPA `@PostUpdate`/`@PostRemove` listener on shareable entities that compares
  `entity.tenantId` to `TenantContext.get()` and calls
  `sharingApi.recordAudit(...)` when they differ.
- Wire `SharedRecordEditedEvent` (already published?) — currently unclear whether the
  CRM services publish it on edits to shared records. Verify and wire if missing.

---

## P0-3 — Three list/search methods still skip ScopeResolver

`LeadService.search` and `LeadService.filter` correctly apply
`scopeResolver.userIdsForCurrentUser()` now (verified at
`api/src/main/kotlin/com/synopticengine/api/crm/lead/service/LeadService.kt`). The
Person/Organization/Activity equivalents do not.

| Service | Method | File:Line | Behaviour |
|---|---|---|---|
| `PersonService` | `search(q, pageable)` | `api/src/main/kotlin/com/synopticengine/api/crm/contact/service/PersonService.kt:44-47` | Calls `personRepository.search(q, pageable)` with no scope. `findAll` at line 32-39 is scoped. |
| `OrganizationService` | `search(q, pageable)` | `api/src/main/kotlin/com/synopticengine/api/crm/contact/service/OrganizationService.kt:31-35` | Calls `organizationRepository.search(q, pageable)` with no scope. `findAll` at line 20-27 is scoped. |
| `ActivityService` | `filter(...)` | `api/src/main/kotlin/com/synopticengine/api/crm/activity/service/ActivityService.kt:29-52` | Takes a `userId` parameter but never resolves the scope. A SALESPERSON with `INDIVIDUAL` view can list everyone's activities by leaving `userId = null`. |

**Plus three CSV exports** in
`api/src/main/kotlin/com/synopticengine/api/crm/contact/service/CrmApiImpl.kt:130-173`:

- `exportPersonsCsv()` — `personRepository.findAllByDeletedAtIsNull()`, no scope.
- `exportOrganizationsCsv()` — same pattern.
- `exportLeadsCsv()` — same pattern.

These are exposed by `ImportController` (`/persons/export`, `/organizations/export`,
`/leads/export`) gated only on `contacts.view`/`leads.view`. A SALESPERSON who can see
five leads in the UI can download the full tenant CSV.

**Same gap on read paths that aren't strictly list/search:**

- `EmailService.findByFolder` (`api/src/main/kotlin/com/synopticengine/api/crm/email/service/EmailService.kt:31-34`)
  has no scope.
- `TagService`, `ProductService`, `WarehouseService` list/search have no scope. These
  may be intentional (tenant-wide catalogue), but the intent isn't documented.

**Fix outline:** Mirror the `LeadService.search`/`searchScoped` pattern for Person,
Organization, and Activity. Add a `searchScoped` query in their repositories with the
same `(:hasScope = false OR created_by IN (:scopeIds))` predicate. Decide and document
whether Tags/Products/Warehouses are tenant-wide (no scope) or owner-scoped.

---

## P1-1 — Async/Scheduled paths drop TenantContext

`AsyncConfig` propagates `TenantContext` across `@Async` boundaries via a
`TaskDecorator` (`api/src/main/kotlin/com/synopticengine/api/shared/config/AsyncConfig.kt:47-58`)
— *if* the context is set at submission time. Two paths submit work without one:

1. **`ShareMaterializationWorker.drainQueue()`**
   (`api/src/main/kotlin/com/synopticengine/api/sharing/service/ShareMaterializationWorker.kt:68-81`)
   runs on `@Scheduled` — there is no request thread, so `TenantContext.get()` is null.
   `runTask()` is `@Transactional` and reads `share_materialization_queue` rows that
   belong to specific tenants, then issues the native query at
   `:137-143` to read the owner-tenant's records. Today, since `@Filter` only applies
   when the GUC is set on the Hibernate session and RLS (P0-1) is inert, those reads see
   every tenant's rows — which is actually what the worker wants here, but only by
   accident. As soon as P0-1 is fixed, the worker breaks unless it sets the right
   tenant context per task.

2. **`WebhookDispatcher.onDomainEvent`**
   (`api/src/main/kotlin/com/synopticengine/api/settings/automation/service/WebhookDispatcher.kt`)
   is `@Async @EventListener`. The decorator only fires if the publisher set
   `TenantContext` — most domain events are fired inside an authenticated request, which
   is fine, but there's no defensive `TenantContext.get() != null` check, so a future
   non-request publisher will silently leak.

3. **`CsvImportProcessor.process(importId)`**
   (`api/src/main/kotlin/com/synopticengine/api/settings/imports/service/CsvImportProcessor.kt:26-27`)
   is `@Async`. Submission happens from `DataImportController` inside a request, so
   `TaskDecorator` propagates — works today. Worth a defensive `TenantContext.get()
   ?: error(...)` at the top of `process` so a future caller doesn't get silent fallback.

**Fix outline:** Each scheduled worker reads its own queue rows and explicitly wraps
the rest of the task body in `TenantContext.runAs(task.tenantId) { ... }`. The
`@Async` decorator stays as the safety net for request-initiated work.

---

## P1-2 — Auth module has no API port; 5 controllers reach into `auth.config`

The other six modules (`identity`, `crm`, `sharing`, `inventory`, `settings`) each
expose a single top-level `*Api` interface and keep their `web/`, `service/`, `repo/`,
`domain/` subpackages internal. **Auth doesn't** — `UserPrincipal` lives at
`api/src/main/kotlin/com/synopticengine/api/auth/UserPrincipal.kt` (top level), and
`auth.config.UserPrincipal` is a `typealias` (`api/src/main/kotlin/com/synopticengine/api/auth/config/UserPrincipal.kt:3`).

Five controllers consistently import the typealias form, reaching into auth's internal
package:

| File | Line | Import |
|---|---|---|
| `crm/datagrid/web/DataGridFilterController.kt` | 3 | `import com.synopticengine.api.auth.config.UserPrincipal` |
| `sharing/web/RecordShareController.kt` | 3 | `import com.synopticengine.api.auth.config.UserPrincipal` |
| `sharing/web/SharePolicyController.kt` | 3 | `import com.synopticengine.api.auth.config.UserPrincipal` |
| `sharing/web/CrossTenantAuditController.kt` | 3 | `import com.synopticengine.api.auth.config.UserPrincipal` |
| `sharing/web/RelationshipController.kt` | 3 | `import com.synopticengine.api.auth.config.UserPrincipal` |

Spring Modulith doesn't flag this because the typealias forwards to the top-level
package (which is the module's named-type export by default). It's still a smell: the
correct import is `com.synopticengine.api.auth.UserPrincipal`.

**Fix outline:** delete the typealias, update the five imports. Optional follow-up:
introduce `AuthApi` if cross-module needs grow beyond a single principal type.

---

## P1-3 — Cross-module @Transactional chains

Found by inspecting every `*Api` impl class and tracing call boundaries:

| Caller | File:Line | Transaction state | Target |
|---|---|---|---|
| `AuthService.resetPassword` | `auth/service/AuthService.kt:113-127` | `@Transactional` | `identityApi.updatePassword(...)` |
| `RecordShareService.share` | `sharing/service/RecordShareService.kt:37` | `@Transactional` | `tenantApi.exists(...)`, `crmApi.findTagsByIds(...)` |
| `TenantRelationshipService.request` | `sharing/service/TenantRelationshipService.kt:38-47` | `@Transactional` | `tenantApi.exists(...)` |
| `ProductService.loadTags` | `inventory/product/service/ProductService.kt:43` | read-only txn | `crmApi.findTagsByIds(...)` |
| `WarehouseService.loadTags` | `inventory/warehouse/service/WarehouseService.kt:46` | read-only txn | `crmApi.findTagsByIds(...)` |

All work today because everything's one Postgres instance. The two write-write chains
(`AuthService.resetPassword`, `RecordShareService.share`) are the ones to think about
if any module ever moves to its own datastore or its own transaction manager. Phase 4
doesn't need to fix this, but `06-implementation-roadmap.md` should note it before
ERP modules with separate schemas land.

---

## P1-4 — Permission registry tests don't catch drift

Three registry tests exist but assert hardcoded key lists rather than diffing the
registry against actual `@PreAuthorize` annotations:

- `api/src/test/kotlin/com/synopticengine/api/identity/IdentityPermissionRegistryTest.kt`
  (42 lines): asserts ~9 keys per registry, contains tests for `Identity`, `Inventory`,
  AND `Settings` in one file (misplaced — `Inventory`/`Settings` should be in their own
  modules' test dirs).
- `api/src/test/kotlin/com/synopticengine/api/crm/CrmPermissionRegistryTest.kt`
  (28 lines): asserts 8 keys. Missing `tags.create` and `pipelines.create` from the
  assertions even though they're both used in `@PreAuthorize` (`TagController`,
  `PipelineController`) — drift the test misses today.
- `api/src/test/kotlin/com/synopticengine/api/sharing/SharingPermissionRegistryTest.kt`
  (20 lines): covers all 9 sharing keys — best of the three.

The actual permission catalog (60 unique `hasAuthority(...)` strings across all
controllers) matches every registry — verified by extracting both sets and diffing.
There is no live drift today. The tests just won't notice if drift appears tomorrow.

**Unused (registered but no `@PreAuthorize` reference):** the **parent** keys —
`users`, `roles`, `groups`, `leads`, `contacts`, `activities`, `quotes`, `mail`,
`products`, `warehouses`, `tags`, `pipelines`, `reports`, `attributes`, `automations`,
`marketing`, `imports`, `records`, `relationships`, `share-policies`, plus
`mail.{inbox,sent,drafts,trash,spam,outbox}`, `tags.{edit,delete}`,
`records.reshare`, `pipelines.{edit,view}`, `marketing.delete`. The parent keys are
intentional (expanded for "ALL" wildcard role permissions in `PermissionExpansion`
logic), but the leaf gaps (`mail.compose`, `mail.inbox`, etc.) look like Krayin-parity
keys registered for the future but not yet enforced.

**Fix outline:** rewrite each `*PermissionRegistryTest` as one auto-derived test in
`api/src/test/kotlin/com/synopticengine/api/AllPermissionsTest.kt`:

```kotlin
@Test fun `every @PreAuthorize key is in some registry`() {
    val annotated = controllerScanner.allHasAuthorityKeys()
    val registered = allRegistries().flatMap { it.permissions() }.map { it.key }.toSet()
    val missing = annotated - registered
    assertTrue(missing.isEmpty(), "Missing in registry: $missing")
}
```

Scanner = `Reflections` or `ApplicationContext.getBeansWithAnnotation(Controller)` +
parse the `@PreAuthorize.value` strings.

---

## P1-5 — 403 coverage gaps

`@PreAuthorize` keys with **no** test asserting 403 for an unprivileged role
(`grep -rn 'assertEquals(403' api/src/test/` finds 28 assertions, covering 27 distinct
keys). Missing:

- `tenants.view`, `tenants.manage`
- `reports.view`
- `pipelines.create`
- `tags.create`
- `imports.create`, `imports.edit`, `imports.view`
- `automations.create`, `automations.edit`, `automations.view`
- `${SharingPermissions.RECORDS_SHARE}` (= `records.share`)
- `${SharingPermissions.RELATIONSHIPS_MANAGE}`, `${SharingPermissions.RELATIONSHIPS_VIEW}`
- `${SharingPermissions.SHARE_POLICIES_MANAGE}`, `${SharingPermissions.SHARE_POLICIES_VIEW}`

The sharing keys are the highest-leverage gap — those endpoints write directly into
the cross-tenant trust graph and have no negative-path test.

**Fix outline:** one parameterised test that iterates `controllerScanner.allHasAuthorityKeys()`
× a non-privileged token and asserts 403 on the GET/POST. ~30 lines, replaces 14 hand-written
mini-tests.

---

## P2-1 — Migration churn between phases

43 migrations in `api/src/main/resources/db/migration/`, including additive churn within
the same week (V018 creates tenants → V019 adds tenant_id to every domain table → V025
adds tenant metadata → V026 per-tenant unique constraints → V034 tenant_relationships).
Several tables get an additive ALTER per phase: `roles` gets `permission_type` in V027,
`leads` gets a flurry across V028/V029/V031/V032/V043.

Per the Phase 4 plan: while still in early dev with no users, consolidating to ~9
baseline migrations grouped by domain dependency would make the schema-of-record obvious
and the lineage easier to reason about. Concrete (this is the Stage 2 plan, restated
here as a finding so it's part of the record):

- V001 core identity (tenants, users, roles, groups, permissions, joins, event_publication,
  password_resets)
- V002 CRM core (tags, lead_sources, lead_types, pipelines, stages, persons, organizations,
  leads, activities, activity_participants, activity_files, quotes, quote_items,
  lead_quotes, lead_products, person_tags, lead_tags)
- V003 inventory (products, product_inventories, warehouses, warehouse_locations,
  product_tags)
- V004 email (emails, email_attachments, email_tags, lead_emails)
- V005 settings (attributes, attribute_options, attribute_values, email_templates,
  web_forms, web_form_attributes, system_configs, data_imports, workflows, webhooks,
  workflow_action_runs, marketing_campaigns, marketing_events)
- V006 datagrid (datagrid_saved_filters)
- V007 sharing (tenant_relationships, tenant_share_policies, record_shares,
  resource_visibility, cross_tenant_audit, share_materialization_queue, RLS policies)
- V008 seed (default tenant, permissions, workflows-of-record)
- V009 notifications

---

## P2-2 — Dashboard "module" is a single controller

`api/src/main/kotlin/com/synopticengine/api/dashboard/` contains exactly one file:
`DashboardController.kt`. There's no `DashboardApi`, no service, no domain — the
controller delegates straight to `crmApi`/`sharingApi`/`identityApi`. Either:

- Collapse `dashboard` into `crm` (it's all CRM reads), or
- Keep it as a thin module and add a `DashboardService` that owns the composition. The
  current shape makes the module list look longer than the actual decomposition.

Same observation, weaker, for `bootstrap/` — it's one `BootstrapService`. Fine to keep
as its own module (lifecycle concern), but worth knowing this is the floor.

---

## P2-3 — `AbstractIntegrationTest` mixes infrastructure and identity concerns

50 of 60 test files extend `AbstractIntegrationTest`. The class
(`api/src/test/kotlin/com/synopticengine/api/AbstractIntegrationTest.kt:1-160`)
provides three orthogonal concerns:

1. **HTTP wrappers**: `get`, `post`, `put`, `delete`, `patch`, `multipart`, `bodyAsMap`,
   `bodyAsList`, `status` — pure infrastructure.
2. **Auth helpers**: `tokenFor`, `adminToken`, `salespersonToken`, `login` — identity
   module concern; pulls in `UserService` as an autowired field, which is the only
   reason the base class needs to know what a user is.
3. **Spring boot context boot** via `@SpringBootTest @AutoConfigureMockMvc
   @Import(TestcontainersConfiguration)`.

The four `*PermissionRegistryTest` and `PermissionExpansionTest` classes don't extend
the base, but five other places hand-roll `userService.create(...)` for their own setup
(`AuthIntegrationTest`, `UserIntegrationTest`, `BootstrapServiceIntegrationTest`,
`LeadSoftDeleteIntegrationTest`, `CreatePermissionBackfillIntegrationTest`) — they
could be using `tokenFor` but aren't.

No `*Factory.kt` files exist under `api/src/test/kotlin/`. Every test does ad-hoc
"create person/lead/activity" via mockMvc inline. This is Stage 3 territory but worth
recording the size: 50 integration tests × ~5 inline data setups each ≈ a lot of
duplication.

---

## P2-4 — No fast-test partition; all tests boot Spring

`api/build.gradle.kts:88-90` has only the default `useJUnitPlatform()` block — no
`@Tag("integration")`, no `unitTests` gradle task. 407 `@Test` methods across 60
files; full `./gradlew test` takes ~2 minutes locally (modulith-only run was
2m 4s from cold start including Gradle distribution download).

Four files are already pure unit tests (no `@SpringBootTest`):

- `bootstrap/PermissionExpansionTest.kt` (50 lines)
- `identity/IdentityPermissionRegistryTest.kt` (42 lines)
- `crm/CrmPermissionRegistryTest.kt` (28 lines)
- `sharing/SharingPermissionRegistryTest.kt` (20 lines)

Plus `TenantContextTest.kt`. These already run fast — the missing piece is **tagging**
them so a `./gradlew unitTests` task can run only those for sub-second feedback
during local work.

Also: every integration test (`@SpringBootTest`) currently boots its own application
context. There's no `@DirtiesContext` and no shared base class with `@TestConfiguration`
optimisation, so Spring should be caching the context across tests with identical
configuration — verify in the actual test report whether that's happening
(`grep "Starting test class:" build/test-results/test/*.xml` should show context reuse).

---

## P3-1 — Two ~60-line methods in `QuoteService`

- `QuoteService.update` — `api/src/main/kotlin/com/synopticengine/api/crm/quote/service/QuoteService.kt:106-169` (63 lines). Field-by-field update + discount recalculation + line-item sync.
- `QuoteService.duplicate` — `api/src/main/kotlin/com/synopticengine/api/crm/quote/service/QuoteService.kt:239-306` (67 lines). Deep clone + recalc.

Both candidates for one "recalculate totals" helper and one "sync items" helper. Not
urgent.

---

## P3-2 — `PersonService.readEntries` silently swallows JSON parse failures

`api/src/main/kotlin/com/synopticengine/api/crm/contact/service/PersonService.kt:208`:

```kotlin
catch (_: Exception) { emptyList() }
```

Stated intent: tolerate legacy JSON. A warn-level log line would let an operator
notice if production data starts hitting this branch.

---

## P3-3 — Three one-shot `@Query` deletes in `ActivityParticipantRepository`

`api/src/main/kotlin/com/synopticengine/api/crm/activity/repo/ActivityParticipantRepository.kt:23,30,37`:
`deleteByActivityIdAndUserId`, `deleteByActivityIdAndPersonId`,
`deleteByParticipantId`. Each is called from exactly one place in `ActivityService`
and is a one-line `DELETE WHERE`. Could be inlined as
`entityManager.createQuery(...)` in the service or — more aligned with Spring Data
JPA convention — kept where they are. Mostly a stylistic preference; flagging only
because the repository is otherwise pure derived queries.

---

## P3-4 — `Permission` and `Tenant` define `@Version` independently of `BaseEntity`

`identity/domain/Permission.kt:32` and `identity/domain/Tenant.kt:62-64` re-define
`@Version var version: Long` instead of extending `BaseEntity` or
`AuditableEntity`. This is **intentional** — both are global catalogues with no
`tenant_id`, so they can't extend `BaseEntity` (whose `@PrePersist` requires a tenant
context). But it means three entity layouts coexist (`BaseEntity`,
`AuditableEntity`, plus this hand-rolled pair). A `GlobalCatalogEntity` mapped
superclass would consolidate. Same finding as 07-P3-1, still present.

---

## P3-5 — `ApplicationTests.contextLoads()` exists but is trivial

`api/src/test/kotlin/com/synopticengine/api/ApplicationTests.kt` boots Spring and
asserts nothing. Cheap insurance against catastrophic wiring breakage; keep but it
shouldn't count toward "tests are green = working app".

---

## What this changes for the Phase 4 plan

The four-stage plan in the task brief still fits, with two adjustments:

1. **Stage 2 (migration consolidation)** is safe to run as planned — no migration
   change can mask P0-1/P0-2 because those are application-code gaps, not schema gaps.
2. **Stage 1 surfaces two genuine Phase 2 deliverables that didn't ship** (P0-1 RLS,
   P0-2 audit log). These are not "Stage 2/3/4" cleanup — they are correctness work
   that should either:
   - Be **a fifth stage** (Stage 0?) before Stage 2, or
   - Be **deferred to a Phase 4.5 / Phase 5** sprint, with the docs (Stage 4) honestly
     marking the items as "designed, not wired up".

   I'd recommend wiring them: each is ~half a day of work and bringing the Phase 2
   docs in line with reality is itself the goal of Stage 4. Doing the doc-pass while
   knowingly papering over an inert RLS layer would be a worse outcome than holding
   Stage 4 until the wiring exists.

3. **P0-3 scope leaks** are isolated to four service methods and three exports — that's
   a half-hour fix; bundle into Stage 3 (test cleanup) so the new factory-based tests
   can prove them shut.

The other P1/P2 items (auth API port, registry-test rewrite, txn chains, async tenant
propagation, dashboard collapse) are good Stage 3 / Stage 4 follow-ups but none of them
blocks the work.

**Recommended call:**

- Stage 1 ✅ this doc.
- Add an explicit decision step before Stage 2: fix P0-1, P0-2, P0-3 first (Stage 1.5,
  same audit branch or a new `feature/phase-4-correctness` branch), or defer
  explicitly. The user should make that call — the alternative is a clean refactor
  on top of a broken Phase 2.
- Stages 2–4 as planned.
