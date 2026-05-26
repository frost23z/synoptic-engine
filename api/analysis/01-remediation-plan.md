# Backend Remediation Plan — `api/`

Date: 2026-05-26
Branch: `claude/festive-edison-LXrzr`
Goal: bring the backend to "truly complete" before frontend work begins.

This plan supersedes the planning role of `00-codebase-audit.md` (which is dated
2026-05-21 and is now **stale** — PRs #31–34 and migrations V014–V017 landed after
it). Every item below was re-verified against the **current** source by a parallel
multi-agent re-audit on 2026-05-26, plus a green `./gradlew testClasses` compile.

---

## Decisions taken (drive scope below)

1. **Deployment: single-node now, multi-node later.** Build single-node, but hide
   node-local state behind interfaces so a distributed backend drops in without a
   rewrite. Concretely: rate limiters and the materialization scheduler stay
   in-process, but (a) rate limiting goes behind a `RateLimiter` interface, and
   (b) the materialization worker gets an optimistic-claim step now with a
   `// MULTI-NODE:` note pointing at `SELECT … FOR UPDATE SKIP LOCKED`. CORS/secrets
   discipline is required regardless of topology.
2. **Architecture rule 6 (Specs): update the rule, do not refactor.** The codebase
   uses hand-written `@Query` uniformly and it works. Edit `CLAUDE.md` rule 6 to
   bless `@Query`-based repositories; do **not** introduce `JpaSpecificationExecutor`
   /`*Specs`. (Same posture for rule 10: document that status enums may use a custom
   `AttributeConverter` for canonical lowercase DB values.)
3. **Scope: CRM parity + security/correctness AND ERP inventory + ops.** In addition
   to Phases 0–7, implement the inventory **movements ledger / reservations /
   transfers / reorder** subsystem (Phase 8) and harden **marketing into a real send
   pipeline** (folded into Phase 3/4).

---

## How to use this plan (for the coding agent)

- Work phase by phase, in order. **Phases 0 and 1 block frontend**; the rest can
  overlap frontend work but should all land before "done."
- Per task: read the cited file(s) first — **line numbers are approximate** and will
  drift as you edit. Confirm the symptom before changing code.
- Verify locally after each task group:
  - `./gradlew testClasses` — fast compile (no Docker).
  - `./gradlew unitTests` — pure unit tests (no Spring/Testcontainers).
  - `./gradlew test` — full suite; **needs Docker** for Testcontainers + the
    `synoptic_app` NOBYPASSRLS role (so RLS actually fires). If Docker is
    unavailable, say so rather than claiming the suite passed.
- New DB changes go in **new Flyway migrations** (next is `V018`). `ddl-auto` is
  `validate`; keep entity ↔ schema in lockstep in the same commit.
- Commit per task group with a descriptive message. Push to the branch above.
- Money is **always `BigDecimal`** with explicit scale + `RoundingMode`. Never `Double`.

## Guardrails — intentional designs, do NOT "fix"

- Sharing-module tables (`record_shares`, `resource_visibility`, `tenant_*`,
  `cross_tenant_audit`, `share_materialization_queue`) intentionally do **not**
  extend `BaseEntity` and have **no** `@Filter`/RLS — they are cross-tenant by
  design; authorization is service-layer.
- RLS policies begin `app_current_tenant() IS NULL OR …` — null-tenant transactions
  (bootstrap, login, public web-form/inbound-mail) bypass RLS **by design**.
- `TenantContext` may be null on public endpoints; `BaseEntity.@PrePersist` is the
  write-side gate.
- Permissions catalog is owned by the per-module `*PermissionRegistry` beans
  (seeded at boot by `BootstrapService`); ADMIN is a wildcard (`RoleType.ALL`)
  expanded to all keys in `UserService.toCredentials`. This is correct.

---

## Phase 0 — Correctness bugs (BLOCKS frontend; small, do first)

- **T0.1 — Quote line-total crash.** `crm/quote/domain/QuoteItem.kt:~42`:
  `lineTotal` uses `BigDecimal.divide(BigDecimal(100))` with no scale/RoundingMode →
  `ArithmeticException` on non-terminating discounts (33.33%, etc.). Fix: use
  `divide(BigDecimal(100), 10, RoundingMode.HALF_UP)` (mirror `QuoteService.kt:~288`).
  Better: delete the entity-level computed property and make
  `QuoteService.Quote.toResponse()` the single source of truth for totals.
  Acceptance: unit test computing a 33.33% line discount returns a value, no throw.
- **T0.2 — Hardcoded sentinel UUIDs.** `crm/lead/domain/Lead.kt:~58,61`
  (`pipelineId`/`stageId` default to seed UUIDs), `crm/quote/domain/Quote.kt:~31`
  (`leadId` = all-zeros), `crm/lead/domain/LeadProduct.kt:~16-19` and
  `QuoteItem.kt:~19` (random UUIDs), and `crm/lead/web/LeadDtos.kt:~17-18`
  (`CreateLeadRequest` defaults). On non-seed tenants these point at non-existent /
  cross-tenant rows. Fix: remove defaults; make FK fields non-defaulted
  (`lateinit`/constructor) and **resolve the tenant's default pipeline/stage in
  `LeadService`** (query `Pipeline.isDefault` + the pipeline's first stage) when the
  request omits them. Acceptance: create a lead in a freshly provisioned tenant with
  no pipelineId → succeeds, points at that tenant's default pipeline.
- **T0.3 — Quote.duplicate back-ref.** `crm/quote/service/QuoteService.kt:~265`
  sets `quoteItem.quoteId` but not the `lateinit var quote` back-ref
  (`QuoteItem.kt:~23`). Fix: set both (mirror `buildItem`, `QuoteService.kt:~141`).
- **T0.4 — massUpdate stage stamp + events.** `crm/lead/service/LeadService.kt:~253`
  `massUpdate` must set `stageUpdatedAt = Instant.now()` when stageId changes and
  publish `lead.stage.changed` (use `publishLeadStageChanged`). Also convert the
  per-id `findActiveById`+`save` loop to a bulk `@Modifying` update (see T5.2).
- **T0.5 — Email send-state guards.** `crm/email/service/EmailService.kt`:
  `forward()` (~199-208) initializes status `SENT` then overwrites to `OUTBOX` —
  set `OUTBOX` once. `sendDraft()` (~144) only blocks `SENT`; also block `OUTBOX`
  (and `SENT`) to prevent double-send.

## Phase 1 — Security must-haves (BLOCKS frontend)

- **T1.1 — Sharing access-level enforcement.** `sharing/domain/AccessLevel.kt`
  defines `canWrite/canReshare/canComment/canDelete` with **zero call sites**.
  `RecordShareService` write/reshare paths rely solely on RLS + `verifyOwnership`,
  and **reshare is broken** (non-owners hard-fail; `RECORDS_RESHARE` unused). Fix:
  (a) on cross-tenant **write/delete** to a shared resource, load the consumer's
  `effectiveAccess()` from `resource_visibility`/`record_shares` and require
  `canWrite()`/`canDelete()` in the service layer (defense-in-depth atop RLS);
  (b) implement reshare: a consumer with `MANAGE` may share onward — gate the
  reshare endpoint on `RECORDS_RESHARE` and check `effectiveAccess().canReshare()`
  instead of `verifyOwnership`. Add integration tests for each level.
- **T1.2 — SSRF on webhooks.** `shared/automation/actions/TriggerWebhookAction.kt:~41`
  and `settings/automation/service/WebhookDispatcher.kt:~61` POST to a tenant-supplied
  `payloadUrl` with no checks. Fix: a shared `OutboundUrlValidator` — require
  `https` (allow `http` only for explicitly configured dev hosts), resolve the host
  and **reject RFC-1918 / loopback / link-local / metadata (169.254.169.254) / IPv6
  ULA**; validate at save time (`AutomationService`) and re-validate at send.
- **T1.3 — Email HTML sanitization (stored XSS).** `shared/email/MailSenderService.kt:~45`
  sends `setText(html, true)`. Sanitize `EmailTemplate.content` and any rendered
  HTML body with an allowlist sanitizer (add `org.jsoup:jsoup`, `Safelist.relaxed()`
  + safe links). Apply in template save and before send (automation + marketing).
- **T1.4 — Auth hardening.**
  - Add `POST /auth/logout` (+ `/auth/logout-all`): revoke the presented refresh
    session (and family / all-by-user). Add `revokeAllByUserId` to
    `RefreshSessionRepository`.
  - `AuthService.resetPassword` (~176) must call `revokeAllByUserId` after a reset.
  - Email-enumeration timing oracle (`AuthService.kt:~44`): in the user-not-found
    branch, run `passwordEncoder.matches(password, DUMMY_BCRYPT_HASH)` to equalize.
  - Replace the unbounded `ConcurrentHashMap` trackers (`LoginAttemptTracker`,
    `ForgotPasswordAttemptTracker`) with a bounded Caffeine cache
    (`maximumSize` + `expireAfterWrite`), behind a `RateLimiter` interface
    (single-node impl now; `// MULTI-NODE:` note for a Redis/bucket4j impl). Fix the
    read-modify-write race by doing the check inside an atomic `compute`.
  - Add `@field:Size(max = 1000)` to `LoginRequest.password` /
    `ResetPasswordRequest.newPassword` (bcrypt-DoS), and `@field:NotBlank` to
    `ForgotPasswordRequest.email` and `ResetPasswordRequest.{token,email}`.
  - `X-Forwarded-For` trust: set `server.forward-headers-strategy=framework` and
    document that the app must sit behind a trusted proxy; use the framework
    `RemoteAddr` rather than parsing the raw header in `AuthController`/web-form.
- **T1.5 — CORS prod discipline.** `application.yaml:~36` default includes
  `http://192.168.*:3000` with `allowCredentials=true` (credential-theft vector).
  Fix: default `CORS_ALLOWED_ORIGINS` to localhost only; require explicit origins in
  non-dev profiles; extend `SecretsGuard` to refuse wildcard origins +
  `allowCredentials` outside dev profiles.
- **T1.6 — Retire the OOM dashboard.** `dashboard/` (top-level)
  `GET /api/dashboard` → `CrmApiImpl.getDashboardLeadStats` (`CrmApiImpl.kt:~157`)
  loads all leads via `PageRequest.of(0, Int.MAX_VALUE)`, ignores view-scope, and is
  only `isAuthenticated()`. Fix: replace its internals with aggregate COUNT/SUM
  queries (reuse `crm/dashboard` stats), add `reports.view`, or delete the endpoint
  in favor of `/api/dashboard/stats`. Decide one; remove the duplicate package.
- **T1.7 — IDOR residuals (use tenant-aware finders / cross-check parent).**
  - `crm/lead/service/LeadService.kt:~313` `attachEmail`: `emailRepository.findById`
    → `emailRepository.findActiveById`.
  - `crm/activity/service/ActivityService.kt:~336` `downloadFile`: verify
    `file.activityId == activityId` (add `findByIdAndActivityId`).
  - `crm/activity/service/ActivityService.kt:~295` `removeParticipantById`: load via
    a tenant-aware finder and check `participant.activityId == activityId` before
    delete (don't `deleteById`).
  - `crm/contact/service/CrmApiImpl.kt:~63` `findOrganizationById`:
    `findById` → `findActiveById`.

## Phase 2 — Tenant isolation defense-in-depth

- **T2.1 — Apply `@Filter` on async threads.** `shared/config/AsyncConfig.kt`
  `TenantPropagatingTaskDecorator` propagates `TenantContext` but not the Hibernate
  filter; async JPQL (WorkflowEngine, WebhookDispatcher) is unscoped except for RLS.
  Fix: register a Hibernate `StatementInspector`/session interceptor (or have async
  entry points call `tenantSession.applyFilter()`) so the filter is enabled whenever
  `TenantContext` is set, regardless of MVC. Add a 2-tenant async regression test.
  (Production-safe today via RLS; this closes the test-fidelity + second-layer gap.)
- **T2.2 — Explicit tenant predicate on dashboard native queries.**
  `crm/lead/repo/LeadRepository.kt` (~10 dashboard natives incl. `openLeadsByStageNative`),
  `crm/contact/repo/PersonRepository.kt:~66`, `OrganizationRepository.kt:~63`: add
  `AND tenant_id = :tenantId` and pass `TenantContext.get()` (mirror
  `ActivityRepository.countCreatedInRangeNative`). Lets integration tests assert
  isolation instead of relying on RLS-only.
- **T2.3 — Cross-tenant FK-consistency triggers (V018).** Audit 6.4 still open. Add
  per-table triggers asserting `tenant_id` equality on FK edges: `quotes→leads`,
  `quote_items→quotes`, `activities→leads/persons/organizations`,
  `lead_products→leads/products`, `product_inventories→products/warehouses/locations`.
- **T2.4 — Secrets at rest.** `webhooks.secret` and `system_configs.value`
  (where `is_secret`) are plaintext. Add an `AttributeConverter`-based envelope
  encryption (AES-GCM, key from env) for these columns; keep response masking.
- **T2.5 — Input caps.** CSV upload: normalize `originalFilename` and assert it stays
  under the upload root (`DataImportService.kt:~51`). Inbound-mail: cap
  `InboundParseRequest.body` size before regex scan. Marketing: `@Email`-validate
  recipients and cap list size (`MarketingService`/`MarketingDtos`).
- **T2.6 — `TenantFilterInterceptor.afterCompletion`** should `disableFilter`
  (LOW; OSIV-safe today) — cheap correctness. Note OSIV is currently relied upon
  (no `spring.jpa.open-in-view=false`); document this coupling.

## Phase 3 — Operational durability (single-node, multi-ready seams)

- **T3.1 — Retention workers.** Add `@Scheduled` pruning for `cross_tenant_audit`
  (keep N days) and `share_materialization_queue` (delete `finishedAt`-old rows).
  Mirror `EventPublicationRetentionWorker`. Make windows configurable.
- **T3.2 — Idempotent materialization upsert + claim.**
  `sharing/service/ResourceVisibilityService.kt:~62` read-then-write is a TOCTOU
  race. Replace with a native `INSERT … ON CONFLICT (consumer_tenant_id,
  resource_type, resource_id, source, source_id) DO UPDATE`. Add an optimistic
  `@Version`/`claimedAt` claim in `ShareMaterializationWorker.drainQueue`
  (`// MULTI-NODE:` → `FOR UPDATE SKIP LOCKED`).
- **T3.3 — Email retry.** No retry for `FAILED`. Add a `@Scheduled` re-sender with
  bounded attempts + backoff columns; keep `OUTBOX→SENT/FAILED` transitions.
- **T3.4 — Webhook retry/backoff.** `WebhookDispatcher` makes one attempt. Add
  bounded retry with exponential backoff using `webhook_delivery_runs`
  (attempt count, next-attempt-at); a `@Scheduled` re-driver.
- **T3.5 — Workflow loop guard.** No cycle detection in `WorkflowEngine`. Add a
  re-entrancy depth/visited-set guard so future service-calling actions can't loop.
  Also collapse the `lead.stage.changed` / `lead.stage_changed` double-publish
  (`LeadService.kt:~452-453`) to a single canonical name + documented alias.

## Phase 4 — Krayin parity completion

- **T4.1 — Attribute validation enforcement.** `validation_rules` (jsonb, V014) is
  stored but never enforced. In `AttributeService.setValue` (~169) evaluate
  `is_required`, `is_unique`, type, regex/pattern, min/max/length from the rules
  before persisting an `AttributeValue`. Also enforce `lookup_type` resolution.
- **T4.2 — Lead auto-assignment.** Add assignment rules (round-robin / by-rule) and
  wire an `assign_*` path on lead create (Krayin convert-on-save expectation).
  Workflow `assign_user/stage/group` actions already exist — reuse the target port.
- **T4.3 — Quote economics (Krayin model).** Add per-item `tax_amount`/`tax_percent`
  and quote-level `discount_amount` vs `discount_percent`; recompute totals as
  `sub_total − discount + tax + adjustment`. Centralize totals (see T0.1). Migration
  for new columns. Render product **name** (not UUID) in `QuotePdfService.kt:~48`.
- **T4.4 — Contact completeness.** `PersonService.merge` (~182) must also reassign
  `activities.person_id` from source→target (add `ActivityRepository.reassignPerson`).
  Add a bulk org-relink endpoint. Route `LeadService.convert` person/org creation
  through `PersonService`/`OrganizationService` (not raw repo) so dual email/phone
  stays in sync and events fire.
- **T4.5 — Consumer shared-resource browse.** `ResourceVisibilityService.visibleIds()`
  has no callers. Add `GET /api/shared/{type}` (paged, filterable) for consumers to
  discover resources shared with their tenant. Apply the QUOTE→LEAD+PERSON cascade
  on share (`CascadeRules` QUOTE branch is currently dead — `RecordShareService.share`
  only cascades LEAD).
- **T4.6 — Misc parity.** Decide lead-rotting semantics (`stageUpdatedAt` vs
  Krayin `created_at`) and expose a `rotten` boolean on `LeadResponse`. Optional:
  quote status-transition guards.

## Phase 5 — Performance (N+1 / bulk)

- **T5.1 — Fetch-joins on list/kanban.** `LeadRepository` list/`filter`/`search`/
  `kanban` and `QuoteRepository` list/`search` return entities whose LAZY
  collections (`tags`, `items`) are then touched in `toResponse()`. Add
  `LEFT JOIN FETCH` (or `@EntityGraph`) for the list paths (mind pagination +
  distinct). `WebFormService.findAll` (~18) does one query per form — batch it.
- **T5.2 — Bulk mutations.** Replace per-id `find`+`save` loops with `@Modifying`
  bulk updates in `LeadService.massUpdate/massDestroy`, `PersonService.massDestroy`,
  `OrganizationService.massDestroy`, and `PipelineService.delete` reparenting. Cap
  `ids` list sizes on the request DTOs.

## Phase 6 — Conventions / cleanup / versions (you asked about these)

- **T6.1 — Finish Jackson 3 migration.** Replace deprecated `JsonNode.asText()` /
  `isTextual` (build warnings) in `sharing/service/TenantSharePolicyFilterEvaluator.kt`
  and the two test files with the Jackson 3 API (`asString()` / `isStringValue` etc.).
  Fix the redundant Elvis in `CrossTenantAuditIntegrationTest.kt:~153`.
- **T6.2 — Update CLAUDE.md** (decision 2): rule 6 → bless `@Query` repositories
  (drop the `JpaSpecificationExecutor`/`*Specs` mandate); rule 10 → note status enums
  may use a canonical-lowercase `AttributeConverter`. Also correct stale lines:
  `clean-on-validation-error` (removed from `application.yaml`), the `AuditableEntity`
  description, and the permission-naming section (registries use `module.action`).
- **T6.3 — Validation/authorization gaps.** Add `@Valid` to controller bodies
  missing it (`LeadController`: moveStage/massUpdate/massDestroy/tag/email/product;
  `QuoteController`: status/massDestroy/sendMail). Add `@PreAuthorize` to
  `DataGridFilterController`. Introduce `tags.*` permissions and re-gate
  `TagController` (currently `leads.*`) since tags are cross-resource.
- **T6.4 — Auditing on async writes.** `JpaAuditingConfig.auditorProvider` reads
  `SecurityContextHolder`, which isn't propagated to `@Async`; the decorator
  propagates `ActorContext`. Make `auditorProvider` fall back to `ActorContext.get()`
  so workflow/import-created rows get `createdBy/updatedBy`.
- **T6.5 — OpenAPI for frontend.** Add an `OpenAPI` bean with a JWT bearer
  `SecurityScheme` (springdoc is already a dependency) and confirm
  `/swagger-ui/**`, `/v3/api-docs/**` are permitted in `SecurityConfig`. This
  unblocks frontend devs testing authenticated endpoints.
- **T6.6 — Seed cleanup + idempotency (V018).** V008 seeds stale orphan `*.create`
  permissions (registries are the source of truth) with `gen_random_uuid()` and no
  `ON CONFLICT`. Remove the redundant permission INSERTs (or make idempotent with
  fixed IDs + `ON CONFLICT DO NOTHING`); same for tenant/pipeline/stages/lookups.
- **T6.7 — Dead code / docs.** Remove dead `PersonRepository.attachTag` (native,
  unused), unused `ActivityService.removeParticipantById` if not exposed, duplicate
  attribute lookup endpoints; fix the stale `RlsTenantGucIntegrationTest` comment
  (RLS DOES fire under the test role now); WarehouseLocation hard-delete orphaning
  (T8 will revisit); LeadSource/LeadType hard-delete vs soft-delete (pick one).
- **T6.8 — Auth code quality.** Parse the JWT once per request (currently ~6 parses
  in `JwtAuthFilter`); raise `BCryptPasswordEncoder` to strength 12; clear
  `SecurityContextHolder` in the filter's finally for virtual-thread safety;
  consider `.claims(map)`-first builder ordering in `JwtTokenProvider`.

## Phase 7 — Tests

- **T7.1** Add a true RLS test: with the `synoptic_app` role, write rows in two
  tenants and assert tenant B reads zero of tenant A's rows (not just "GUC is set").
- **T7.2** Sharing: materialization crash-safety (kill mid-task → `finishedAt` set,
  no orphan/dup on retry), and a `CrossTenantAction.SHARE` audit-row test.
- **T7.3** Replace `Thread.sleep(500)` in `DataImportIntegrationTest.kt:~85` with a
  status-poll loop.
- **T7.4** Add pure unit tests (no Spring) for the money/totals logic (T0.1/T4.3),
  the URL validator (T1.2), the rate limiter (T1.4), and JWT provider.
- **T7.5** Async 2-tenant isolation test backing T2.1; per-access-level sharing
  tests backing T1.1.

## Phase 8 — ERP inventory + ops (decision 3)

Today: `Product`, `Warehouse`, `WarehouseLocation`, `ProductInventory`
(mutable qty-by-location). Build the real model:

- **T8.1 — Movements ledger.** New append-only `inventory_movements`
  (type: RECEIPT/ISSUE/ADJUST/TRANSFER_IN/TRANSFER_OUT/RESERVE/RELEASE, qty,
  product, from/to location, ref doc, actor, tenant). Make on-hand a **derived**
  sum, not a mutable column (or keep a cached balance updated transactionally with
  the ledger as the source of truth). Soft/hard-delete decision documented.
- **T8.2 — Stock states.** Track on-hand / reserved / available / in-transit /
  damaged per product+location (projected from the ledger).
- **T8.3 — Reservations.** Reserve/release stock against a reference (e.g. quote/
  order); enforce no-negative-available.
- **T8.4 — Transfers.** Warehouse→warehouse transfer orders (two-leg movements,
  in-transit state, receive step).
- **T8.5 — Reorder points** + low-stock surfacing (and optionally a workflow event).
- **T8.6 — Fix `WarehouseLocation`** hard-delete orphaning `ProductInventory`
  (FK + block-delete-if-stock, or soft-delete).
- **T8.7 — (Sub-decision)** cost method (FIFO vs weighted-average) — confirm before
  building; default to weighted-average if unspecified.
- **T8.8 — Marketing send pipeline.** Promote `MarketingService.executeCampaign`
  from a synchronous fan-out to a queued, batched, rate-limited sender with
  per-recipient tracking (sent/failed/opened if feasible) and retry; reuse T1.3
  sanitization and T3.3/T3.4 retry patterns.

---

## Severity rollup (for triage)

- **Must-fix before frontend:** T0.1–T0.5, T1.1–T1.7.
- **High:** T2.1–T2.2, T3.1–T3.2, T4.1, T5.1, T6.5.
- **Medium:** remainder of Phase 2–6.
- **Larger build-outs:** Phase 8 (ERP), T4.2–T4.5 (parity), T8.8 (marketing).

## Already verified DONE since the old audit (do not re-open)

Refresh-token rotation + family revocation (V015), login lockout, hashed reset
tokens + forgot-password throttling, `SecretsGuard`, RLS on 38 tables (V007+V011),
native-query tenant predicates for email/activity/quote dashboards (C1–C4),
IDOR sweep across 12 services (Tag/Automation/DataImport/Attribute/WebForm/
EmailTemplate/Organization/LeadSource/DataGridFilter/SettingsApi/CrmWorkflowTarget/
UserService), datagrid column allowlist (H12), async SMTP (H14), path-traversal
hardening (H13), contact merge, lead conversion, lead rotting endpoint, quote PDF,
all 10 workflow actions, web-form CAPTCHA + create-lead, SystemConfig per-tenant
(H9/V012), template interpolation (M6), `filterJson` evaluator, event-publication
retention (6.7), NOBYPASSRLS test role wiring. Build is green on JDK 25 / Spring
Boot 4.0.6 / Kotlin 2.3.21 / jjwt 0.13.0.
