# Codebase Audit — `api/`

Date: 2026-05-21
Scope: `/api/src/main/kotlin` (275 files) + `/api/src/test/kotlin` (73 files) + Flyway migrations V001–V010.
Method: Parallel deep-dive agents, then manual verification of every CRITICAL/HIGH finding.

This document records gaps, latent bugs, design concerns, and recommended cleanups.
Findings claimed by agents but contradicted by source were dropped; the items below
are verified against actual code (file:line cited).

> **Reading order**: Section 1 (executive summary + verified bug list) is the only thing
> you must read. Sections 2–10 are per-area deep dives, mostly useful when you pick up
> a specific item.

---

## 1. Executive summary

The codebase is **substantially solid** — Spring Modulith boundaries, Hibernate
tenant filter + Postgres RLS + per-`@Transactional` GUC aspect, factory-based
integration test suite, comprehensive sharing module. The architecture itself is
in the top tier for a Krayin-parity attempt.

However, several **multi-tenant correctness bugs** were found in native SQL
queries that bypass both Hibernate's `@Filter` and Postgres RLS (because RLS is
only enabled on 4 of ~43 tenant tables). These are the highest-priority items
and are listed first below.

### 1.1 Verified CRITICAL bugs (must fix before any production traffic)

| # | File:Line | Bug | Why it leaks |
|---|-----------|-----|--------------|
| C1 | `crm/email/repo/EmailRepository.kt:13–27` | `findByFolder` is a native query with no `tenant_id` filter and the `emails` table has **no RLS policy** (V007 only covers leads/orgs/persons/products). | Every authenticated request to `GET /mail/{folder}` returns emails from every tenant. |
| C2 | `crm/activity/repo/ActivityRepository.kt:83–97` | `countCreatedInRangeNative` — native, no `tenant_id`, `activities` has no RLS. | Dashboard activity counts merge across tenants. |
| C3 | `crm/activity/repo/ActivityRepository.kt:120–144` | `findOverlappingMeetings` — native, no `tenant_id`, no RLS. | Meeting-overlap check sees every tenant's calendar; can also reveal *that* another tenant has a meeting at the same time. |
| C4 | `crm/quote/repo/QuoteRepository.kt:102–116` | `countCreatedInRangeNative` — native, no `tenant_id`, no RLS on `quotes`. | Quote dashboard counts merge across tenants. |
| C5 | `crm/contact/repo/PersonRepository.kt:65–76` | `countCreatedInRangeNative` — native, no `tenant_id`. **Mitigated** by `persons` RLS in V007, but only because the GUC aspect runs; if the aspect ever skips (e.g. `@Transactional(readOnly=true)` propagated to a non-tx path), it leaks. | Defense-in-depth gap. |
| C6 | `identity/repo/UserRepository.kt:44–53` | `findGroupMemberIds` is a native query against `user_groups` with no tenant filter. Used to resolve `ViewContext` for "same group" view permission. | A user in group X in tenant A also sees user-IDs of unrelated tenant-B users who happen to share a `group_id` UUID — which won't collide in practice, BUT the query also has no constraint that the joined `user_groups` row's user lives in the current tenant. |
| C7 | `auth/service/AuthService.kt:28–29` + `identity/service/UserService.kt:42–43` | Login calls `findCredentialsByEmail` which uses JPQL with no tenant filter (TenantContext is null at login). If a future migration relaxes the `UNIQUE (email)` constraint to `(email, tenant_id)` to enable B2B multi-tenant signups, the **first** matching row wins silently → cross-tenant credential collision. | Latent today (current schema enforces global unique email) but actively prevents multi-tenant signup which is the stated product goal. |
| C8 | V008 seed migration | Not idempotent — re-running on an existing DB will violate PKs / produce duplicate `lead_sources` (uses `gen_random_uuid()` instead of fixed IDs). | `flyway repair` / re-baseline scenarios will break. **Severity revised**: Flyway only ever runs each migration once based on `flyway_schema_history`, so practical impact is limited to dev-environment re-baseline and rare ops scenarios. Treat as **MEDIUM**, not blocker. |
| C9 | Systemic IDOR via `JpaRepository.findById` / `existsById` in service layer | Hibernate's `@Filter("tenantFilter")` only rewrites HQL/JPQL/Criteria. Primary-key loads via `EntityManager.find()` (the path used by `JpaRepository.findById`, `existsById`, `getReferenceById`) **bypass the filter entirely**. Many services (`EmailService.findById` + 8 other call sites; `PersonService.requirePerson`; `UserService.requireUser` + `massDeactivate`; `RoleService.requireRole` + `delete`; `TagService` 3×; `AutomationService` 6×; `DataImportService` 8×; `AttributeService` 3×; `WebFormService`; `EmailTemplateService` 2×; `OrganizationService`; `LeadSourceService` 4×; `DataGridFilterService`; `SettingsApiImpl` 2×) use `findById`/`existsById` for the post-load tenant scope check. Outcome: `GET /<entity>/{id}` for another tenant's resource returns 200 OK with the row, instead of 404. **Mitigated in production** by Postgres RLS (now extended in V011) once the app runs as a `NOBYPASSRLS` role, but the test suite runs as BYPASSRLS=true so the test layer would not catch it. Defense-in-depth requires both layers. |

### 1.2 Verified HIGH-priority gaps

| # | Area | Bug / gap |
|---|------|-----------|
| H1 | RLS coverage | V007 only enables RLS on 4 tables. Every other tenant-scoped table (`activities`, `emails`, `quotes`, `attributes`, `workflows`, `webhooks`, `web_forms`, `data_imports`, `email_templates`, `marketing_*`, `pipelines`, `stages`, `tags`, `warehouses`, `products` is covered, `product_inventories`, `roles`, `groups`, `users`, …) relies on the Hibernate `@Filter` alone, which **does not apply to native SQL**. Pair with C1–C5 above. |
| H2 | Cross-tenant audit completeness | `cross_tenant_audit` only logs EDIT, not SHARE/REVOKE — owner publishes both events, the listener's `actorTenantId != ownerTenantId` guard rejects them. (`sharing/events/CrossTenantAuditEventListener.kt:23–28`, verified.) Owner can't see *that* a share was granted in the audit timeline. |
| H3 | Expired sharing rows | `record_shares.expires_at` and `resource_visibility.expires_at` are filtered at read time but rows are never deleted. No `@Scheduled` cleanup. DB will grow unbounded; index `idx_rv_expiry` will degrade. |
| H4 | Materialization worker crash recovery | `sharing/service/ShareMaterializationWorker.kt:104–120` — if `runTask()` crashes mid-batch, `finishedAt` is never set, the task is re-picked and the upsert can produce unique-constraint violations rolling back the second run. Need try-finally + idempotent upsert. |
| H5 | Refresh-token rotation | `AuthService.refresh` issues new access *and* new refresh tokens but the old refresh token remains valid until natural expiry (7 days). A leaked refresh token grants 7 days of access regardless of legitimate-user activity. Should rotate + revoke (token family). |
| H6 | No login rate limiting / account lockout | `auth/web/AuthController.kt:21–29` — `/auth/login` and `/auth/forgot-password` are unthrottled. Brute force is open-bandwidth. |
| H7 | Password-reset token stored in plaintext | `auth/domain/PasswordReset.kt` + `AuthService.kt:89–105` — token saved verbatim; emailed verbatim. DB read access ⇒ full account takeover. Should store BCrypt hash and compare on use. |
| H8 | Object-level authorization (IDOR) on identity endpoints | e.g. `identity/web/UserController.getById(id)` — does not verify the loaded user belongs to the caller's tenant. The Hibernate filter is enabled for the request, so this happens to work today, but **breaks when invoked outside the request scope** (async, scheduled, future system endpoints). Service-layer should `require(user.tenantId == TenantContext.get())`. |
| H9 | `SystemConfig` is not tenant-scoped at the JPA layer | `settings/config/domain/SystemConfig.kt` — the table has a `tenant_id` column at the SQL level (V005), but the JPA entity does **not** model it and the primary key is `code` alone. Writes from one tenant therefore overwrite another's config (PK collision), reads ignore tenant entirely. To fix: change the PK to `(tenant_id, code)`, add a `tenant_id` field to the entity backed by `BaseEntity`, and migrate existing data into the seed tenant. V011 enables RLS on the table so cross-tenant *reads* are at least filtered out at the DB layer, but the PK collision on writes still needs the application change. |
| H10 | Native query in `EmailRepository` also bypasses soft-delete and folder scoping correctness | folders are `jsonb`, query uses `@>` operator with `jsonb_build_array` — fine, but combined with C1 the impact is that `GET /mail/sent` returns every tenant's sent items. |
| H11 | `BootstrapService` default admin credentials | `application.yaml:38–40` provides `SYNOPTIC_ADMIN_EMAIL=admin@synoptic.dev`/`SYNOPTIC_ADMIN_PASSWORD=Admin@123` as fallbacks — i.e. a fresh deploy without env vars boots with a known login. Should fail-fast in production profile. |
| H12 | Datagrid filter input not whitelisted | `crm/datagrid/service/DataGridFilterService` accepts a free-form `applied: Map<String, Any>` payload that downstream code dereferences. Without a column-name allowlist, any future filter executor that builds dynamic SQL is one step from injection. |
| H13 | LocalStorageService path traversal | `shared/storage/LocalStorageService.kt` — `load(path)` resolves a caller-provided string against the storage root with no canonicalisation. Combined with future file-download endpoints, `../../etc/passwd` is reachable. |
| H14 | Email send inside request transaction | `crm/email/service/EmailService.compose` sends SMTP synchronously inside `@Transactional`. SMTP latency + outage holds the DB connection. Should publish a `MailRequested` event and send out-of-band. |
| H15 | No idempotency in V008 seed | See C8. |

### 1.3 Verified MEDIUM-priority items

Grouped, see Section 3 for citations. These are real but not blockers:

- M1. CORS allows `http://192.168.*:3000` wildcard (`application.yaml:36`) — useful for dev, dangerous if it ships.
- M2. Activity overlap detection method `validateSchedule` *exists* and is wired, but the underlying native query is the leaky one in C3.
- M3. Missing partial indexes `WHERE deleted_at IS NULL` on `pipelines`, `stages`, `quotes`, `persons` lookup paths — already present on some tables but not consistently.
- M4. Hardcoded default UUIDs in entity initializers (`Lead.pipelineId = '...010'`, `Quote.leadId = '...000'`). These are wrong on tenants whose seed used different IDs; load defaults from the service.
- M5. `Person` has both legacy scalar `email`/`phone` and JSON `emails`/`contactNumbers` arrays. Dual-write is hand-rolled and de-syncs on partial updates.
- M6. `EmailTemplate.content` is stored as raw HTML and sent verbatim — no `{{firstName}}` substitution exists. The "send email to person" workflow action sends the literal template.
- M7. CSV `DataImport` has no rollback — row 50 failing leaves rows 1–49 committed.
- M8. `EmailTemplate.content` admin-controlled but never sanitized — XSS in rendered mails if the inbox renders HTML.
- M9. `LocalStorageService` has no file-size limit and no MIME validation.
- M10. `RESHARE`, `COMMENT`, `MANAGE` access levels are defined in `AccessLevel.kt` and even appear in the docs/permissions, but no controller / service path enforces them. Either implement or remove.
- M11. Workflow trigger model: events are *published* by services but very few are *subscribed* — `WorkflowEngine` only fires on the configured event names. Verify each CRM service actually publishes the names workflows expect ("lead.created", "lead.stage_changed", etc.). Currently mismatched in several places.
- M12. `web_form_attributes` and `activity_participants` use surrogate UUID PKs where natural composite keys would be safer and faster.
- M13. JPA-level FK consistency: `quotes → leads`, `activities → leads/persons`, etc. carry both rows' `tenant_id`s but no CHECK or trigger enforces equality. A bug elsewhere can produce cross-tenant edges that pass DB constraints.
- M14. `event_publication` table has no retention story — will grow unbounded for high-volume tenants.
- M15. Async `TaskDecorator` propagates `TenantContext`, but there is no equivalent protection for `@Scheduled` methods. `ShareMaterializationWorker` does it by hand (correctly) — but no guardrail prevents future `@Scheduled` beans from running with a null context.
- M16. `Java toolchain = 25` in `build.gradle.kts` while Spring Boot 4.0.6's tested matrix is 21/24. Pin to 21 for safety.

### 1.4 Reasonable design choices — DO NOT "fix"

These were flagged by audit agents but are deliberate and correct:

- Sharing-module entities (`RecordShare`, `TenantSharePolicy`, `ResourceVisibility`, `CrossTenantAudit`, `TenantRelationship`, `ShareMaterializationTask`) do **not** extend `BaseEntity` and do **not** have `@Filter("tenantFilter")`. They are intentionally cross-tenant tables; tenant authorization is done at the service layer.
- `TenantContext` can be null on public endpoints (login, web-form submission, inbound mail). `BaseEntity.@PrePersist` errors on writes without a tenant; reads are caller-responsible.
- RLS policies start with `app_current_tenant() IS NULL OR …` so null-tenant transactions bypass RLS — this is intentional for bootstrap and public flows. The application enforces tenant boundary in those paths by other means (`TenantContext.runAs`).
- The Hibernate `@Filter` doesn't fire in tests because Testcontainers' default Postgres user is BYPASSRLS; `RlsTenantGucIntegrationTest` only proves the GUC is set, not that policies fire. This is acknowledged in `RlsTenantGucAspect.kt:39–44`. **Action**: see Section 6.5.
- Cascade rules in sharing module are intentionally not recursive (hardcoded LEAD→PERSON/ORG, QUOTE→LEAD/PERSON). Not a bug.

---

## 2. Krayin parity gaps (1st MVP)

Mapped against `krayin-features-analysis/`. Items below are confirmed missing or
only partially implemented.

### 2.1 Lead module
- **Lead rotting** (P1). `Pipeline.rottenDays` exists but no query/filter computes rotten leads. `LeadRepository.filter()` does not expose a rotten filter, no `@Scheduled` job tags or surfaces them. Add `findRottenLeads(pipelineId, asOf)` returning leads where `stageUpdatedAt < now - pipeline.rottenDays`.
- **Lead conversion to person/deal** (P1). No endpoint to convert an open lead into a `Person` + optional `Organization` linkage in one transaction. Krayin requires this on lead-save.
- **Lead products** present and correctly modelled (`LeadProduct`).
- **Quote phases** (P1). `QuoteStatus` only has DRAFT in the codebase by default — verify SENT/ACCEPTED/REJECTED/EXPIRED transitions are wired in `QuoteService`.
- **Lead assignment rules** (P2). No round-robin or rule-based auto-assignment. The `WorkflowAction` registry doesn't include "assign user/group/stage".
- **Lead source / type** modelled and seeded — OK.

### 2.2 Contact module
- **Person ↔ Organization linking** is modelled but no bulk re-link operation.
- **Contact merge** (P1) — missing entirely. No `mergePerson(sourceId, targetId)` service; no controller endpoint. This is a Krayin-listed feature.
- Dual scalar/JSON email/phone fields on `Person` — see M5 above. Pick one.

### 2.3 Activity module
- **Calendar overlap** (P1) — `findOverlappingMeetings` is implemented but leaks across tenants (C3). Fix is to add `AND a.tenant_id = :tenantId` and supply it from `TenantContext`.
- **Activity files** are wired and tested. OK.
- **Activity participants** modelled; surrogate PK only — see M12.

### 2.4 Email module
- **Inbound parse** (P1) — endpoint exists, HMAC signature verified, but the parser does not extract lead/person references from the message body. Threading by `In-Reply-To` and `References` headers is partial.
- **Folders** are jsonb-based; tenant-scoped — but the only list query is the broken native one (C1).
- **Email drafts / send-later / OUTBOX** — only DRAFT and SENT exist; no OUTBOX/FAILED status for retries.
- **Email template variables** — see M6. Implement Handlebars/Mustache-style substitution with a fixed safe context.

### 2.5 Quote module
- PDF export — `QuotePdfService` exists; verify there is a `GET /quotes/{id}/pdf` endpoint and the output passes a sanity test. (Quick check: not present in `QuoteController` per the audit; add it.)
- Discounts, tax breakdown, terms — minimal in `Quote`. Krayin has richer line-item modelling (per-item discounts, tax categories).

### 2.6 Settings / automation
- **Custom attributes** (P1). Storage and EAV table are present. Missing: validation column, `is_required`/`is_unique` enforcement, `lookup_type` resolution, `quick_add` flag for inline forms, batch fetch on list (currently N+1 in `AttributeService`).
- **Workflow trigger publication** (P0). Verify each CRM service publishes the events workflows reference. Audit confirms `WorkflowEngine` is wired correctly but several CRM mutation paths don't actually fire the matching `DomainEvent`.
- **Workflow actions**: 6 present (`AddNoteActivityAction`, `AddTagAction`, `SendEmailToPersonAction`, `SendEmailToSalesOwnerAction`, `TriggerWebhookAction`, `UpdateLeadAction`, `UpdatePersonAction`). Missing: assign-user, assign-stage, assign-group. Add these for parity.
- **Web forms** — submission flow works, but **no CAPTCHA**. Public endpoint, in-memory rate limiter only. Add reCAPTCHA v3 / hCaptcha verification before persisting.
- **Web form styling fields** missing — Krayin's `background_color`, `submit_success_action` etc. not on `WebForm` entity.
- **Marketing campaigns** — entity exists; no execution / send pipeline.

### 2.7 Settings / system config
- Not multi-tenant (H9). This is structural and needs schema change + migration.

### 2.8 Dashboard
- Stats are reasonable; queries are mostly native and need tenant fix (C2/C4).
- "Top salespeople" via `findTopSalespeople` is OK (JPQL, filter applies).
- Calendar view via `findCalendarRange` (JPQL) is OK.

---

## 3. Multi-tenant isolation — detailed findings

Layer model:

1. `TenantContext` (ThreadLocal) populated by `JwtAuthFilter`.
2. Hibernate `@Filter("tenantFilter")` enabled per request by `TenantFilterInterceptor`.
3. Postgres RLS via `app.current_tenant` GUC set by `RlsTenantGucAspect`.

This is a strong design — three independent layers. The bugs are in implementation, not architecture.

### 3.1 Native queries — Hibernate filter does NOT apply (CRITICAL)

Hibernate `@Filter` only rewrites HQL/JPQL/Criteria. Native SQL is passed through as-is.

| Repository | Method | Has RLS fallback? |
|------------|--------|-------------------|
| `EmailRepository.findByFolder` | native, no tenant | NO RLS on `emails` ⇒ LEAKS (C1) |
| `ActivityRepository.countCreatedInRangeNative` | native, no tenant | NO RLS on `activities` ⇒ LEAKS (C2) |
| `ActivityRepository.findOverlappingMeetings` | native, no tenant | NO RLS on `activities` ⇒ LEAKS (C3) |
| `QuoteRepository.countCreatedInRangeNative` | native, no tenant | NO RLS on `quotes` ⇒ LEAKS (C4) |
| `PersonRepository.countCreatedInRangeNative` | native, no tenant | RLS protects (V007) — but defense-in-depth gap (C5) |
| `LeadRepository.*Native` (7 methods) | native, no tenant | RLS protects (V007) |
| `LeadProductRepository.findAllWithProductInfoByLeadId` | native, joins `lead_products + products`. Caller passes `leadId`; if the lead itself was loaded via a tenant-scoped query, only that lead's products are returned — but the query does not re-check tenant. RLS on `products` protects the join; `lead_products` has no RLS. | Risky |
| `UserRepository.findGroupMemberIds` | native, no tenant on `user_groups` | NO RLS on `user_groups` — could in principle cross tenants (C6) |

**Fix pattern (apply to all):**
```kotlin
@Query(value = """
    SELECT COUNT(*) FROM activities
    WHERE deleted_at IS NULL
      AND tenant_id = :tenantId
      AND created_at >= :start AND created_at < :end
      AND (:hasScope = false OR user_id IN (:scopeIds))
""", nativeQuery = true)
fun countCreatedInRangeNative(
    @Param("tenantId") tenantId: UUID,
    @Param("start") start: Instant,
    @Param("end") end: Instant,
    @Param("hasScope") hasScope: Boolean,
    @Param("scopeIds") scopeIds: Collection<UUID>,
): Long
```
Service passes `TenantContext.get() ?: error("...")` for `tenantId`.

### 3.2 RLS coverage gap (HIGH)

V007 enables RLS on 4 tables: `leads`, `organizations`, `persons`, `products`.

Tables holding tenant data but with **no RLS policy** today:
`activities`, `activity_files`, `activity_participants`, `emails`, `email_attachments`, `quotes`, `quote_items`, `pipelines`, `stages`, `lead_products`, `lead_sources`, `lead_types`, `tags`, `lead_tags`, `person_tags`, `product_tags`, `email_tags`, `warehouses`, `warehouse_locations`, `product_inventories`, `attributes`, `attribute_options`, `attribute_values`, `workflows`, `workflow_action_runs`, `webhooks`, `webhook_delivery_runs`, `web_forms`, `web_form_attributes`, `data_imports`, `email_templates`, `marketing_campaigns`, `marketing_events`, `datagrid_saved_filters`, `users`, `groups`, `roles`, `user_groups`, `user_roles`.

Add a new migration V011 that enables RLS on every BaseEntity-backed table with the standard policy template:

```sql
ALTER TABLE public.activities ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_activities_rw ON public.activities
    USING ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()))
    WITH CHECK ((app_current_tenant() IS NULL) OR (tenant_id = app_current_tenant()));
```
(No `app_has_visibility(...)` clause for tables not in the resource-sharing matrix.)

### 3.3 Login bypasses tenant (CRITICAL but latent)

`AuthService.login` calls `IdentityApi.findCredentialsByEmail(email)`. `TenantContext` is null at this point and the JPQL query does not include `tenant_id`. The current schema has `UNIQUE (email)` (V001:162) so only one row can match — *but that means email is the de-facto tenant identifier*, which directly conflicts with the multi-tenant signup story.

When you implement per-tenant signups, either:
- **Option A** (recommended): keep email globally unique across the platform. Tenant is then derived from the user row that matches.
- **Option B**: switch to `UNIQUE (email, tenant_id)` and require a tenant hint at login (subdomain/slug). At that point, `findCredentialsByEmail(email)` *must* take a `tenantHint` and re-filter.

Document the decision before touching code.

### 3.4 BaseEntity SEED_TENANT_ID default

`BaseEntity.tenantId = TenantContext.SEED_TENANT_ID` is overwritten by `@PrePersist`, which errors if `TenantContext.get()` is null. This is **safe today** — the hook fires before INSERT for every JPA write. Two caveats:

1. JDBC writes (Flyway migrations, raw `JdbcTemplate`) bypass the hook. Only `V008__seed.sql` does this today; OK because that's intentionally seed-tenant data.
2. If anyone later adds a `BulkLoader` using `JdbcTemplate`, they must explicitly set `tenant_id`. Add a `// HOWTO comment` in `BaseEntity.kt` next to the default.

---

## 4. Cross-tenant sharing module — detailed findings

The unique product differentiator. Module is well-structured but has gaps in
maintenance / lifecycle.

### 4.1 Materialization worker (HIGH)
`sharing/service/ShareMaterializationWorker.kt:104–120`. If `runTask()` throws,
`finishedAt` is not set; the same row is picked up on the next tick. Combined
with the existing UNIQUE constraint on `resource_visibility`, the second run
will hit a constraint violation and roll back — leaving the visibility set
*partially* materialized.

Fix:
- Wrap the per-task body in `try { … } finally { task.finishedAt = Instant.now(); save(task) }`.
- Make the upsert idempotent: `INSERT … ON CONFLICT (consumer_tenant_id, resource_type, resource_id, source, source_id) DO UPDATE SET access_level = EXCLUDED.access_level, expires_at = EXCLUDED.expires_at` instead of the current "delete-then-insert" or naive insert.

### 4.2 SHARE / REVOKE audit gap (HIGH)
`sharing/events/CrossTenantAuditEventListener.kt:23–28`. Owner publishes both
`RecordSharedEvent` and `RecordShareRevokedEvent`. The listener requires
`actorTenantId != ownerTenantId` to write an audit row, but for these events
the actor *is* the owner, so they're silently dropped.

Audit log is now incomplete for the **most important** action in the product.

Fix: add a separate `OwnerActionAudit` row type, or extend the existing table
with an `actor_role` (OWNER/CONSUMER) column and drop the same-tenant guard.

### 4.3 Expiry cleanup (HIGH, operational)
`record_shares.expires_at` and `resource_visibility.expires_at` are filtered at
read time; rows are never deleted. Add a daily `@Scheduled` job:
```kotlin
@Scheduled(cron = "0 0 3 * * *")
fun cleanupExpired() { … } // delete WHERE expires_at < now() - INTERVAL '7 days'
```
Keep a 7-day grace window for forensic queries.

### 4.4 RESHARE / COMMENT / MANAGE not enforced (MEDIUM)
Defined in `sharing/domain/AccessLevel.kt` and `SharingPermissions.kt`, but no
controller/service actually checks them. Either (a) implement, or (b) remove from
the public enum/registry to avoid misleading consumers via OpenAPI.

### 4.5 Policy `filterJson` is a stub
`TenantSharePolicyService.kt:45–49` explicitly rejects non-null `filterJson` and
`ShareMaterializationWorker.matchesFilter(...)` returns `true` for everything.
This is acknowledged in code; mark as "Sprint 2c" with a TODO that links to a
ticket so it's tracked.

---

## 5. Auth & security — detailed findings

### 5.1 Refresh token reuse (HIGH)
`AuthService.refresh` re-issues both tokens but the old refresh is still valid
until JWT expiry. Implement rotation:

- On refresh: include a `family_id` and `jti` claim, persist `(userId, family_id, jti_hash, expires_at, revoked_at)`.
- New refresh => insert next `jti` in same family.
- If a refresh is presented whose `jti` is already used / revoked => revoke the whole family (token theft signal). 401 + force re-login.

### 5.2 Login rate-limit / lockout (HIGH)
None today. Even basic in-memory bucketing per (IP, email) → 429 after 5
failures in 15 minutes would be a meaningful improvement. Long-term: move to
Redis-backed `bucket4j`.

### 5.3 Password-reset token hygiene (HIGH)
`PasswordReset.token` stored verbatim. Steps:
- Store `BCrypt.hashpw(token)` in the DB; compare on use.
- Send the plaintext token only in the email body (already done).
- Reduce TTL: 60 min → 15 min.
- Rate-limit `/auth/forgot-password` per email per day.

### 5.4 CORS (MEDIUM)
`application.yaml:36` allows `http://192.168.*:3000`. Useful for dev. In
production profile, either remove the wildcard or load via env-only.

### 5.5 Default JWT secret and admin password (HIGH)
- `JWT_SECRET` has an insecure default literal in `application.yaml:32`.
- `SYNOPTIC_ADMIN_PASSWORD` defaults to `Admin@123`.
On the production profile, both should `fail-fast` if env vars are missing.
Add a `@PostConstruct` check that errors out when the literal default is
detected outside of `local` / `test` profiles.

---

## 6. Schema & migrations — detailed findings

### 6.1 Indexes (MEDIUM/HIGH)
Missing composite `(tenant_id, …)` indexes for hot list queries:
- `activities (tenant_id, user_id)`, `(tenant_id, lead_id)`, `(tenant_id, person_id)` — kanban/calendar/owner views.
- `emails (tenant_id, person_id)`, `(tenant_id, lead_id)`.
- `quotes (tenant_id, lead_id)`.
- `product_inventories (tenant_id, product_id, warehouse_id)`.
- `datagrid_saved_filters (tenant_id, user_id, src)`.

Add partial indexes where `deleted_at IS NULL` clauses are common: `pipelines`, `stages`, `quotes`, `tags`.

### 6.2 RLS migration V011 (HIGH)
Section 3.2.

### 6.3 Soft-delete inconsistency (HIGH)
The following entities/tables either have a `SoftDeletable` interface but no
`deleted_at` column, or vice versa: `product_inventories`, `warehouse_locations`,
`lead_products`, `quote_items`, `lead_sources`, `lead_types`, `tags`,
`web_form_attributes`, `attribute_options`. Pick a position per table — soft
or hard — and align Kotlin + SQL.

### 6.4 Tenant-consistent FKs (MEDIUM)
For every FK that joins two tenant-scoped rows, add a CHECK constraint or a
deferred trigger that enforces `tenant_id` equality. Example:
```sql
ALTER TABLE quotes
    ADD CONSTRAINT chk_quote_tenant_matches_lead
    CHECK (
      (lead_id IS NULL) OR
      (tenant_id = (SELECT l.tenant_id FROM leads l WHERE l.id = lead_id))
    ) NOT VALID;
```
(NOT VALID because it can't be enforced as a CHECK referencing another table — needs a trigger; the SQL above is for illustration only.)

The cleanest implementation is a per-table trigger:
```sql
CREATE FUNCTION assert_quotes_tenant_matches_lead() RETURNS trigger AS $$
BEGIN
  IF NEW.lead_id IS NOT NULL THEN
    PERFORM 1 FROM leads
      WHERE id = NEW.lead_id AND tenant_id = NEW.tenant_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'cross-tenant FK on quotes.lead_id'; END IF;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
```
Apply pattern to: `activities → leads/persons/organizations`, `quotes → leads/persons`, `lead_products → leads`, `quote_items → quotes/products`, `product_inventories → products/warehouses/warehouse_locations`.

### 6.5 RLS test coverage (MEDIUM)
`RlsTenantGucIntegrationTest` proves the GUC is set, but Testcontainers runs as
superuser (BYPASSRLS), so policies never *fire* in tests. Two options:

1. In `TestcontainersConfiguration`, run a startup script that creates a
   non-superuser `synoptic_app` role with `NOBYPASSRLS`, grants schema/table
   privileges, and reconfigures the Hikari datasource user to that role.
2. Or run the integration suite twice — once as superuser (current), once as
   `synoptic_app` — to cover both paths.

Recommended: option 1 with a profile flag.

### 6.6 V008 seed idempotency (CRITICAL for ops)
- Tenant insert at L12 uses fixed UUID — add `ON CONFLICT DO NOTHING`.
- Pipeline / stages: fixed UUIDs — same.
- `lead_sources`/`lead_types`: use fixed UUIDs (not `gen_random_uuid()`) so re-runs are no-ops.

### 6.7 `event_publication` retention (MEDIUM)
Add a Flyway repeatable migration or scheduled job to archive completed events older than 30 days into `event_publication_archive` and prune the archive older than 180 days. Otherwise the table is unbounded.

---

## 7. Service layer & infrastructure — detailed findings

### 7.1 `EmailService.compose` sends inside the request transaction (HIGH)
`crm/email/service/EmailService.kt:101–106`. SMTP latency / outage = held DB
connection. Refactor to:
1. Persist the email row with status = OUTBOX.
2. Publish a `MailRequested` domain event.
3. An async handler (`@Async` + retry) sends and transitions to SENT or FAILED.

### 7.2 `LocalStorageService` path traversal (HIGH)
- Canonicalise `Path.resolve(path).toRealPath()`.
- Assert `path.startsWith(storageRoot.toRealPath())`.
- Reject `..` segments before resolving.
- Enforce file size and MIME on the upload boundary, not the storage layer.

### 7.3 `GlobalExceptionHandler` coverage (LOW)
Handler maps `NoSuchElementException → 404`, `IllegalArgumentException → 400`,
`IllegalStateException → 409`, validation → 422, generic → 500. Good. Minor
adds:
- Map `AccessDeniedException` → 403 (today it's caught by Spring Security; verify the response shape matches the rest of the API).
- Map `OptimisticLockingFailureException` → 409 with a specific code so clients can retry.

### 7.4 `AsyncConfig` task decorator (LOW)
Correctly propagates `TenantContext` *and* `ActorContext`. Good. Add a unit test
proving the decorator clears both on completion (especially on exception).
`AsyncTenantContextIntegrationTest` exists but covers the happy path only.

### 7.5 Java toolchain (LOW)
`build.gradle.kts:19` sets language version 25. Spring Boot 4.0.6's tested
matrix is 21+. Pin to 21 unless you specifically need 25 features.

---

## 8. Inventory module — gaps for ERP

Today the module has: `Product`, `Warehouse`, `WarehouseLocation`,
`ProductInventory` (qty-by-location only). For real ERP you eventually need:

- Stock statuses (on-hand / reserved / in-transit / damaged).
- Inventory movements ledger (immutable append-only) — current state should be
  derived from movements, not stored as a single mutable qty.
- Transfer orders between warehouses.
- Batches / lots / serials with expiry.
- Reservations against orders.
- Cost methods (FIFO / weighted average).
- Reorder points and replenishment rules.

For the CRM-parity MVP this is fine. Just document that the module is intentionally minimal.

---

## 9. Tests — gaps to plug

(Coverage is generally strong: ~73 integration tests, factories, modulith verification.)

- T1. No RLS-policy enforcement test — see 6.5.
- T2. No login-with-expired-JWT test (only validate / invalid tokens).
- T3. No test that the sharing materialization worker is crash-safe (kill mid-batch, restart, assert no orphans).
- T4. No SHARE / REVOKE audit-log test (because of bug H2 — once fixed, add the test).
- T5. No test for `EmailRepository.findByFolder` cross-tenant leak (because the bug exists). Add a regression test that creates emails in two tenants and asserts each only sees its own.
- T6. `Thread.sleep(500)` in `DataImportIntegrationTest` — replace with polling loop on import status.

---

## 9b. Fixed in this audit pass

The mechanical native-query tenant leaks in Band A.1 and the broader RLS
coverage gap in Band A.2 were addressed across two PRs (#25 and the one
containing this revision).

### PR #25 — native-query tenant predicates

| File | Change |
|------|--------|
| `crm/email/repo/EmailRepository.kt` | `findByFolder` now requires `tenantId` (C1). |
| `crm/email/service/EmailService.kt` | Passes `TenantContext.get()` when calling `findByFolder`. |
| `crm/activity/repo/ActivityRepository.kt` | `countCreatedInRangeNative` and `findOverlappingMeetings` require `tenantId` (C2, C3). |
| `crm/activity/service/ActivityService.kt` | Passes tenant on `checkOverlap`. |
| `crm/quote/repo/QuoteRepository.kt` | `countCreatedInRangeNative` requires `tenantId` (C4). |
| `crm/dashboard/service/DashboardStatsService.kt` | New `requireTenant()` helper; passes tenant to all native dashboard counts on `activities` and `quotes`. |
| `identity/repo/UserRepository.kt` | `findGroupMemberIds` now joins `users` and filters by `tenant_id` (C6). |
| `identity/service/UserService.kt` | Passes tenant when resolving GROUP view scope. |

### Hardening batch (current branch) — finish IDOR sweep + sharing + auth + SystemConfig + indexes

A single multi-commit branch that closes most of the audit's
CRITICAL/HIGH backlog in one pass:

| Commit | Audit refs | Summary |
|--------|------------|---------|
| `fix(api): finish IDOR sweep across remaining services` | C9 | 29 files; switched every remaining `findById` / `existsById` site in 14 services to a tenant-aware `findActiveById` JPQL finder, plus added the same method to 9 more repositories. Deletes load-then-`delete(entity)` so the filter runs before the SQL. |
| `feat(sharing): crash-safe materialization + SHARE/REVOKE audit + expiry cleanup` | H2, H3, H4 | `ShareMaterializationWorker.runTask` wrapped in try-finally so `finishedAt` is always set. `CrossTenantAuditEventListener` now logs `SHARE` and `REVOKE` (guard in service relaxed for owner-side actions). New `ShareExpiryCleanupWorker` daily-cron prunes expired rows from `record_shares` + `resource_visibility` past a configurable grace window. |
| `feat(auth): login lockout + hashed reset tokens + secrets guard` | H6, H7, H11 | New `LoginAttemptTracker` (in-memory, per `(email, IP)` ; configurable thresholds; throws `LoginLockedOutException` → 429). `AuthService.forgotPassword` issues 256-bit URL-safe Base64 token, stores BCrypt hash, shortened TTL (15 min default). `SecretsGuard` `@PostConstruct` refuses to start on non-dev profiles when JWT secret or admin password is at default. |
| `fix(settings): SystemConfig per-tenant primary key + seeding listener` | H9 | V012 promotes `system_configs` PK from `(code)` to `(tenant_id, code)`. JPA entity gains `tenantId` field + `@IdClass` + Hibernate `@Filter("tenantFilter")` + `@PrePersist`. `SystemConfigTenantInitListener` copies catalogue from seed tenant on every `TenantProvisionedEvent`. |
| `feat(db): missing composite (tenant_id, …) indexes` (V013) | 6.1 | Composite indexes for hot list/join paths on activities, emails, quotes, product_inventories, warehouse_locations, datagrid_saved_filters, attribute_values, attribute_options. All `IF NOT EXISTS`. |

### PR #27 — IDOR fix on email + person/user/role + regression tests

Discovered when running PR #26's `EmailTenantIsolationIntegrationTest` in a
local environment: the get-by-id assertion failed because
`EmailService.findById` used `JpaRepository.findById(id)`, which goes through
`EntityManager.find()` and bypasses Hibernate's `@Filter("tenantFilter")`
(the filter is a query rewriter; it does not see primary-key loads). Same
pattern across other services, see new finding **C9**.

Fixed in this PR (consistent `findActiveById` pattern via JPQL):

| File | Change |
|------|--------|
| `crm/email/repo/EmailRepository.kt` | Added `findActiveById(id)` + `existsActiveById(id)` (JPQL). |
| `crm/email/service/EmailService.kt` | All `findById` / `existsById` / `deleteById` call sites (9 of them) routed through `requireEmail` / `findActiveById`. `delete` now loads first then calls `delete(entity)` so the `@SQLDelete` soft-delete trigger fires under the filter. |
| `crm/contact/service/PersonService.kt` | `requirePerson` now uses `personRepository.findActiveById` (JPQL) instead of `findById`. |
| `identity/repo/RoleRepository.kt` | Added `findActiveById(id)` JPQL. |
| `identity/service/RoleService.kt` | `requireRole` uses `findActiveById`; `delete(id)` loads-then-deletes via the entity, not `deleteById`. |
| `identity/service/UserService.kt` | `requireUser` uses `findActiveByIdWithRolesAsList(id).firstOrNull()` (already JPQL); `massDeactivate` no longer routes through `findById`. |
| `test/.../crm/PersonTenantIsolationIntegrationTest.kt` | New regression — Tenant B fetching Tenant A's person id must not return 200. |

**Still pending** (same pattern across services not touched in this PR — tracked under C9):

`TagService` (3 sites), `AutomationService` (6 sites), `DataImportService`
(8 sites), `AttributeService` (3 sites), `WebFormService`,
`EmailTemplateService` (2 sites), `OrganizationService.update` path,
`LeadSourceService` (4 sites), `DataGridFilterService`, `SettingsApiImpl`
(2 sites), `CrmWorkflowTargetAdapter.findTag`, `UserService.existsById`
(IdentityApi contract — needs a tenant-aware variant).

### V011 + regression tests

- `db/migration/V011__rls_baseline.sql` — enables Postgres RLS on every
  remaining BaseEntity-backed table (33 tables). Sharable resource families
  (`activities`, `quotes`, `warehouses`) use the visibility-aware policy
  matching V007; everything else uses the simple `tenant_id = app_current_tenant()`
  policy. The sharing-module tables (record_shares, resource_visibility,
  cross_tenant_audit, tenant_relationships, tenant_share_policies,
  share_materialization_queue) are intentionally not RLS-protected — they
  are cross-tenant by design.
- `test/.../crm/EmailTenantIsolationIntegrationTest.kt` — provisions two
  tenants, asserts each only sees its own emails via `GET /mail?folder=sent`.
- `test/.../crm/ActivityOverlapTenantIsolationIntegrationTest.kt` —
  provisions two tenants, asserts the meeting-overlap query does not leak
  Tenant A's calendar to Tenant B even when B passes A's user id.

**Not changed in this pass** (deferred — see Band A.2 etc.):
- Lead/Person native dashboard queries (defense-in-depth; already protected by RLS in V007).
- ~~The RLS migration V011~~ ✅ done in this PR.
- The V008 idempotency fix (severity revised to MEDIUM; Flyway's run-once
  model limits the practical impact).
- Everything in Bands B–F.

## 10. Recommended priority queue

Status after PR #25, PR #26, PR #27, and the hardening batch
(this branch):

**Band A — Multi-tenant correctness:** ✅ Done.
1. ~~Native-query tenant predicates (C1–C6)~~ — PR #25.
2. ~~V011 RLS baseline~~ — PR #26.
3. V008 idempotency (C8) — DEFERRED (severity revised to MEDIUM; Flyway only runs once).
4. ~~Cross-tenant regression tests~~ — PR #26 + #27.
5. ~~Systemic IDOR via `findById`~~ — PR #27 (Email/Person/User/Role) + hardening batch (everywhere else).

**Band B — Sharing module hardening:** ✅ Done in hardening batch.
6. ~~Crash-safe `ShareMaterializationWorker.runTask`~~ — H4.
7. ~~SHARE/REVOKE in `cross_tenant_audit`~~ — H2.
8. ~~Daily expiry cleanup~~ — H3 (`ShareExpiryCleanupWorker`).

Still open:
- Idempotent upsert in the worker (ON CONFLICT DO UPDATE) — the try-finally
  fixes the visible-state-after-crash issue, but two workers racing on the
  same task can still hit the unique constraint. Lower priority once the
  retry loop is short.

**Band C — Auth hardening:** Partial — done in hardening batch except
refresh-token rotation.
9. Refresh-token rotation + family revocation (H5) — OPEN.
10. ~~Login rate limit + lockout~~ — H6.
11. ~~Hash password-reset tokens, shorten TTL~~ — H7.
12. ~~Fail-fast on default secrets~~ — H11 (`SecretsGuard`).

Still open:
- Rate-limit `/auth/forgot-password` (H7 partial — login is done, forgot-password isn't).

**Band D — Schema cleanups:** Mostly done.
13. ~~Composite `(tenant_id, …)` indexes~~ — V013.
14. Soft-delete consistency cleanup (6.3) — OPEN.
15. Cross-tenant FK triggers (6.4) — OPEN.

**Band E — Krayin parity catch-up (still 1 week of work):**
16. Lead rotting query + dashboard surface.
17. Lead → person conversion endpoint.
18. Contact merge endpoint.
19. Email template variable substitution (Handlebars or similar).
20. Web-form CAPTCHA.
21. CSV import transactional rollback.
22. Workflow actions: assign-user, assign-stage, assign-group.
23. ~~Make `SystemConfig` tenant-scoped (H9)~~ — done in hardening batch.

**Band F — Long tail (ongoing):**
- Custom-attribute validation/required/unique/lookup.
- Quote PDF endpoint + richer line-item modelling.
- Inventory movements ledger.
- Marketing campaign execution.
- `event_publication` retention.
- TestcontainersConfiguration: non-superuser DB role so V011 RLS actually fires in CI.

---

## Appendix — files cited in this report

(For navigation. Add ctrl-click targets in your IDE.)

```
api/src/main/kotlin/com/synopticengine/api/
  Application.kt
  shared/
    TenantContext.kt
    domain/{BaseEntity,AuditableEntity,GlobalCatalogEntity,SoftDeletable}.kt
    config/{RlsTenantGucAspect,TenantFilterInterceptor,WebMvcConfig,AsyncConfig}.kt
    storage/LocalStorageService.kt
    web/GlobalExceptionHandler.kt
  auth/
    service/AuthService.kt
    config/{JwtAuthFilter,JwtTokenProvider,SecurityConfig}.kt
  identity/
    repo/UserRepository.kt
    service/UserService.kt
  crm/
    activity/repo/ActivityRepository.kt
    contact/repo/PersonRepository.kt
    email/repo/EmailRepository.kt
    lead/repo/{LeadRepository,LeadProductRepository}.kt
    quote/repo/QuoteRepository.kt
    email/service/EmailService.kt
  sharing/
    service/{ShareMaterializationWorker,RecordShareService,TenantSharePolicyService}.kt
    events/CrossTenantAuditEventListener.kt
    domain/{AccessLevel,RecordShare,ResourceVisibility,TenantSharePolicy,CrossTenantAudit}.kt
  settings/config/domain/SystemConfig.kt
  src/main/resources/db/migration/
    V001..V010__*.sql
  src/main/resources/application.yaml
```
