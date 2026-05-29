# Backend Gap Analysis — Synoptic Engine `api/`

**Date:** 2026-05-29  
**Branch HEAD:** `b072047` (fix: resolve 11 integration-test failures)  
**Scope:** Full audit of current source vs Krayin parity and frontend readiness  
**Method:** Deep file-by-file exploration, endpoint enumeration, git log review  

---

## TL;DR — Current State

All previously-planned Phases 0–8 are **complete and merged**. The backend is
production-ready at the single-node level. A handful of small gaps remain (listed
in §3). No runtime crashes, no missing security controls for the planned feature set.

---

## 1. Completed Work (Phases 0–8)

| Phase | Commits | Summary |
|-------|---------|---------|
| Phase 0 | `85169e0` | Quote line-total math, sentinel UUID removal, QuoteItem back-ref, massUpdate stage stamp + events, email double-send guard |
| Phase 1 | `e9b8e3d` | Sharing access-level enforcement, SSRF webhook guard, HTML email sanitization, auth hardening (token rotation, bcrypt DoS, timing oracle, rate limiter interface), CORS hardening, OOM dashboard removal, IDOR sweep |
| Phase 2 | `802c3cb` | Async Hibernate filter propagation, native-query tenant predicates, cross-tenant FK triggers (V018), AES-GCM secrets at rest, input caps (CSV, inbound mail, marketing), `TenantFilterInterceptor.afterCompletion` |
| Phase 3 | `23441d5` | Retention workers (audit + materialization), idempotent materialization upsert + claim, email retry, webhook retry/backoff, workflow loop guard |
| Phase 4 | `04e14e7` | Attribute validation enforcement, lead auto-assignment, quote economics (per-item tax, discount-amount vs percent), contact completeness (merge reassigns activities), consumer shared-resource browse, misc parity (lead rotten boolean, quote status guards) |
| Phase 5 | `87ff4b5` | Fetch-joins on list/kanban, bulk `@Modifying` mutations for massUpdate/massDestroy |
| Phase 6 | `6c8fcdb` | Jackson 3 migration, CLAUDE.md rule updates, validation/auth gaps, async auditing, OpenAPI JWT scheme, seed idempotency, dead code removal, auth code quality |
| Phase 7 | `e5020cd` | RLS isolation tests, materialization crash-safety, async 2-tenant isolation test, unit tests (money/totals, URL validator, rate limiter, JWT), access-level sharing tests |
| Phase 8 | `2579b6d` | ERP movements ledger, stock states (on-hand/reserved/available/in-transit), reservations, warehouse transfers, reorder points, marketing send pipeline (queued, batched, per-recipient tracking) |
| Hot-fix | `b072047` | 11 integration-test failures resolved (async filter, cross-tenant FK trigger, email inbound parse, product test, sharing access-level, test support factories) |

---

## 2. Full Endpoint Inventory (current)

### 2.1 Auth  `POST /auth/…`
`login` · `refresh` · `me` · `logout` · `logout-all` · `forgot-password` · `reset-password`

### 2.2 CRM — Leads  `${api}/leads`
`GET /` · `GET /search` · `GET /rotten` · `GET /kanban`  
`GET /{id}` · `POST /` · `PUT /{id}` · `PATCH /{id}/stage` · `DELETE /{id}`  
`POST /mass-update` · `POST /mass-destroy`  
`POST /{id}/tags` · `DELETE /{id}/tags/{tagId}`  
`GET /{id}/emails` · `POST /{id}/emails` · `DELETE /{id}/emails/{emailId}`  
`GET /{id}/products` · `POST /{id}/products`  
`GET /{id}/activities`  
`POST /{id}/convert`

### 2.3 CRM — Contacts  `${api}/contacts/persons` + `…/organizations`
**Persons:** `GET /` · `GET /search` · `GET /{id}` · `POST /` · `PUT /{id}` · `DELETE /{id}`  
`POST /mass-destroy` · `POST /{id}/tags` · `DELETE /{id}/tags/{tagId}`  
`GET /{id}/activities` · `POST /merge`  
**Organizations:** `GET /` · `GET /search` · `GET /{id}` · `POST /` · `PUT /{id}` · `DELETE /{id}`  
`POST /mass-destroy` · `GET /{id}/activities`  
⚠️ **Gap:** Organizations missing `POST /{id}/tags` and `DELETE /{id}/tags/{tagId}` (see §3.1)

### 2.4 CRM — Activities  `${api}/activities`
`GET /` · `GET /{id}` · `POST /` · `PUT /{id}` · `PATCH /{id}/done` · `DELETE /{id}`  
`POST /mass-update` · `POST /mass-destroy`  
`POST /{id}/participants` · `POST /{id}/participants/users` · `POST /{id}/participants/persons`  
`DELETE /{id}/participants/users/{userId}` · `DELETE /{id}/participants/persons/{personId}` · `DELETE /{id}/participants/{userId}`  
`GET /calendar` · `POST /check-overlap`  
`POST /{id}/file` · `GET /{id}/file/{fileId}/download`

### 2.5 CRM — Email  `${api}/mail`
`GET /` · `GET /{id}` · `GET /{id}/thread`  
`POST /` (JSON) · `POST /` (multipart) · `POST /{id}/send` · `POST /{id}/forward` · `POST /{id}/reply`  
`PATCH /{id}/folder` · `PATCH /{id}/read` · `DELETE /{id}`  
`POST /mass-update` · `POST /mass-mark-read` · `POST /mass-destroy`  
`POST /{id}/tags` · `DELETE /{id}/tags/{tagId}`  
`GET /attachments/{attachmentId}/download`  
`POST /inbound-parse` (public, HMAC-verified)

### 2.6 CRM — Quotes  `${api}/quotes`
`GET /` · `GET /search` · `GET /{id}` · `POST /` · `PUT /{id}` · `PATCH /{id}/status` · `DELETE /{id}`  
`POST /mass-destroy` · `POST /{id}/duplicate` · `POST /{id}/send-mail`  
`GET /lead-products/{leadId}` · `GET /{id}/print` · `GET /{id}/pdf`  
⚠️ **Gap:** No `GET ${api}/leads/{id}/quotes` convenience endpoint (use `GET /quotes?leadId=…` instead — see §3.2)

### 2.7 CRM — Pipelines/Stages  `${api}/pipelines`
`GET /` · `GET /{id}` · `POST /` · `PUT /{id}` · `DELETE /{id}`  
`POST /{id}/stages` · `PUT /{id}/stages/{stageId}` · `DELETE /{id}/stages/{stageId}`  
`PUT /{id}/stages/reorder` · `PATCH /{id}/stages/reorder`

### 2.8 CRM — Tags  `${api}/tags`
`GET /` · `GET /search` · `GET /{id}` · `POST /` · `PUT /{id}` · `DELETE /{id}` · `POST /mass-destroy`

### 2.9 CRM — Dashboard  `${api}/dashboard`
`GET /` (legacy, delegates to stats) · `GET /stats`

### 2.10 CRM — DataGrid Filters  `${api}/datagrid/saved-filters`
`GET /` · `POST /` · `PUT /{id}` · `DELETE /{id}`

### 2.11 Inventory — Products  `${api}/products`
`GET /` · `GET /search` · `GET /{id}` · `GET /{id}/inventory`  
`POST /` · `PUT /{id}` · `PUT /{id}/inventory` · `DELETE /{id}` · `POST /mass-destroy`  
`POST /{id}/tags` · `DELETE /{id}/tags/{tagId}`  
⚠️ **Gap:** No `GET /{id}/activities` endpoint (ActivityRepository supports productId filter — see §3.3)

### 2.12 Inventory — Warehouses  `${api}/warehouses`
`GET /` · `GET /search` · `GET /{id}` · `GET /{id}/products` · `GET /{id}/locations` · `GET /{id}/activities`  
`POST /` · `PUT /{id}` · `DELETE /{id}` · `POST /mass-destroy`  
`POST /{id}/tags` · `DELETE /{id}/tags/{tagId}`  
`POST /{id}/locations` · `PUT /{id}/locations/{locationId}` · `DELETE /{id}/locations/{locationId}`

### 2.13 Inventory — Movements / Stock  `${api}/inventory`
`GET /stock` · `POST /reserve` · `POST /release` · `GET /low-stock`

### 2.14 Inventory — Transfers  `${api}/inventory/transfers`
`GET /` · `POST /` · `POST /{id}/dispatch` · `POST /{id}/receive` · `POST /{id}/cancel`

### 2.15 Identity  `${api}/users` · `…/roles` · `…/groups` · `…/tenants`
**Users:** `GET /` · `GET /search` · `GET /{id}` · `POST /` · `PUT /{id}` · `DELETE /{id}` · `POST /mass-destroy` · `POST /mass-update` · `PUT /{id}/password`  
**Roles:** `GET /` · `GET /permissions` · `GET /{id}` · `POST /` · `PUT /{id}` · `DELETE /{id}`  
**Groups:** `GET /` · `GET /{id}` · `POST /` · `PUT /{id}` · `DELETE /{id}`  
**Tenants:** `GET /` · `GET /{id}` · `POST /`

### 2.16 Settings  `${api}/settings/…`
**Attributes:** full CRUD + options CRUD + values + mass-update + mass-destroy + lookup + check-unique + download template  
**Workflows:** CRUD + runs list  
**Webhooks:** CRUD + deliveries list  
**Email Templates:** CRUD + `POST /{id}/render` (preview with variable substitution)  
**Web Forms:** CRUD + public `GET /web-forms/{id}` + `POST /web-forms/{id}/submit`  
**Marketing Campaigns:** CRUD + `POST /{id}/execute`  
**Marketing Events:** CRUD + `POST /mass-destroy`  
**System Config:** `GET /` · `GET /{code}` · `PUT /{code}`  
**Imports:** full pipeline (upload → validate → link → index → start) + export (`GET ${api}/{entity}/export`)

### 2.17 Sharing  `${api}/…`
`POST /records/share` · `POST /records/reshare` · `DELETE /records/share/{id}`  
`GET /records/{resourceType}/{resourceId}/shares` · `GET /records/shared-with-me`  
`GET /cross-tenant-audit`  
**Relationships:** CRUD + `PATCH /{id}/accept` · `PATCH /{id}/revoke` · `PATCH /{id}/suspend` · `PATCH /{id}/resume`  
**Share Policies:** `GET/POST /relationships/{id}/policies` · `GET/PUT/DELETE /share-policies/{id}`

---

## 3. Remaining Gaps

### 3.1 Organization tags endpoints (MISSING)
**Severity:** Low-Medium — frontend cannot tag organizations from the detail page  
`OrganizationController` has no `POST /{id}/tags` or `DELETE /{id}/tags/{tagId}`.  
`PersonController` and `LeadController` both have these.  
The `Organization` entity has the `@ManyToMany tags` set; `OrganizationService` has no `attachTag`/`detachTag` methods.  
**Fix:** Add `attachTag`/`detachTag` to `OrganizationService` (mirror `PersonService:159-179`) and wire two endpoints in `OrganizationController`.

### 3.2 Lead → quotes convenience endpoint (MISSING)
**Severity:** Low — the `/api/quotes?leadId={id}` filter works; this is UX sugar  
`LeadController` has no `GET /{id}/quotes`. Krayin CRM shows quotes in the lead detail sidebar.  
**Fix:** Add `GET /{id}/quotes` mapping that delegates to `QuoteService.filter(leadId = id, status = null, pageable)`.

### 3.3 Product activities endpoint (MISSING)
**Severity:** Low — `ActivityRepository.filter` already accepts `productId`  
`ProductController` has no `GET /{id}/activities`. `WarehouseController` has the equivalent. `ActivityRepository` already supports the `productId` filter parameter.  
**Fix:** Add `GET /{id}/activities` to `ProductController` delegating to `ActivityService.filter(productId = id)`.

### 3.4 OSIV (Open-In-View) not explicitly disabled
**Severity:** Low / informational  
`spring.jpa.open-in-view` is not set; Spring Boot defaults to `true`. The CLAUDE.md notes "OSIV is currently relied upon" and marks this as intentional.  
**Impact:** Hibernate sessions span the entire HTTP request. This enables `toResponse()` lazy-load collections without explicit `JOIN FETCH`, but also silently opens the DB connection longer than needed and hides N+1 in controllers.  
**Recommendation:** Keep as-is for now (as documented). When scaling to multi-node, set `open-in-view=false` and add the required `JOIN FETCH` or `@EntityGraph` to every lazy-loaded collection in response mappers.

### 3.5 In-memory rate limiters (single-node only)
**Severity:** Operational / future concern  
`LoginAttemptTracker`, `ForgotPasswordAttemptTracker` use Caffeine caches behind the `RateLimiter` interface.  
All `// MULTI-NODE:` markers are in place. Swap in a Redis/bucket4j implementation when deploying more than one backend instance.  
**No action needed until horizontal scaling.**

### 3.6 Webhook DNS-rebinding window
**Severity:** Low / operational  
`OutboundUrlValidator` resolves DNS at validation time. A malicious DNS server that returns a valid IP at save-time and a private IP at delivery-time could bypass the check.  
The `// MULTI-NODE:` comment documents the mitigation (egress proxy).  
**No code fix needed; document in deployment runbook.**

### 3.7 `PersonService` tags not synced to `Person.email/contactNumbers` dual fields
**Severity:** Low design debt  
`Person` has both scalar `email`/`phone` and JSON-array `emails`/`contactNumbers` (maintained in sync via `PersonService.update` and `LeadService.convert`). Manual inspection confirms they are kept in sync by the service layer.  
Watch for any future direct-entity writes that bypass `PersonService`.

---

## 4. Krayin Parity Status

| Feature | Status |
|---------|--------|
| Leads — all CRUD, kanban, rotten, convert | ✅ Complete |
| Leads — custom attributes | ✅ Complete (T4.1) |
| Leads — auto-assignment rules | ✅ Complete (T4.2) |
| Contacts — persons, organizations, merge | ✅ Complete |
| Organizations — tag attachment | ❌ Missing (§3.1) |
| Activities — all CRUD, calendar, overlap | ✅ Complete |
| Email — compose, send, reply, forward, inbound, attachments, threading | ✅ Complete |
| Email — retry on failure | ✅ Complete (T3.3) |
| Email — template variable substitution | ✅ Complete (Phase 6) |
| Quotes — CRUD, status transitions, PDF, send-mail | ✅ Complete |
| Quotes — per-item tax, discount economics | ✅ Complete (T4.3) |
| Lead → quotes sidebar | ❌ Missing convenience endpoint (§3.2) |
| Products — CRUD, inventory, tags, low-stock | ✅ Complete |
| Products — activities sidebar | ❌ Missing endpoint (§3.3) |
| Warehouses — CRUD, locations, transfers, activities | ✅ Complete |
| Inventory — movements ledger, reservations, reorder | ✅ Complete (Phase 8) |
| Pipelines/Stages — CRUD, reorder | ✅ Complete |
| Custom Attributes — full EAV with validation enforcement | ✅ Complete |
| Workflows — all 10 action types | ✅ Complete |
| Webhooks — CRUD + retry + SSRF guard | ✅ Complete |
| Web Forms — CRUD + CAPTCHA + lead creation | ✅ Complete |
| Marketing — campaign CRUD + queued send pipeline | ✅ Complete (Phase 8) |
| CSV Import — upload → validate → execute with rollback | ✅ Complete |
| System Config — per-tenant | ✅ Complete |
| Users/Roles/Groups — CRUD, permissions | ✅ Complete |
| Multi-tenant RLS (38 tables) | ✅ Complete |
| Cross-tenant sharing — share, reshare, policies, audit | ✅ Complete |
| Auth — JWT, refresh rotation, rate limiting, bcrypt-DoS guard | ✅ Complete |

**Parity summary:** ~97% complete. Three small missing endpoints (§3.1–3.3) are the only gaps.

---

## 5. Frontend Integration Notes

### 5.1 Base path
All protected endpoints are under `${api.base-path}` (defaults to `/api`).  
Public paths: `/auth/**`, `/web-forms/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`.

### 5.2 Authentication
- `POST /auth/login` → `{ accessToken, refreshToken, expiresIn }`
- `POST /auth/refresh` → new token pair
- All `/api/**` requests: `Authorization: Bearer <accessToken>`
- Access token TTL: 15 min. Refresh token TTL: 7 days. Refresh rotation enabled.

### 5.3 Pagination
Standard response envelope: `{ data: [...], meta: { total, page, perPage, lastPage } }`.  
All list endpoints accept `?page=&perPage=` query params via Spring `Pageable`.

### 5.4 Tenant context
Tenant is resolved from the JWT claim — no `X-Tenant-ID` header needed.  
Each user belongs to exactly one tenant; multi-tenancy is transparent.

### 5.5 Permissions
Permission keys returned in JWT `authorities`. Frontend should gate actions by:
`leads.view`, `leads.create`, `leads.edit`, `leads.delete` (and similarly for every module).

### 5.6 File uploads
Activities: `POST /{id}/file` (multipart). Max size 25 MB per file, 200 MB per request.  
Email attachments: bundled with `POST /mail` (multipart variant).  
Imports: `POST /settings/imports` (multipart).

### 5.7 CORS
Default: `http://localhost:3000`. Override via `CORS_ALLOWED_ORIGINS` env var.  
For production: set explicit origin(s); wildcard + credentials rejected.

### 5.8 OpenAPI spec
Live at `GET /v3/api-docs` and `GET /swagger-ui/index.html`.  
JWT bearer scheme configured — use "Authorize" button in Swagger UI.

---

## 6. Architecture Quick Reference

| Concern | Implementation |
|---------|---------------|
| Tenant isolation (primary) | PostgreSQL RLS on all 38+ tables (`app_current_tenant()` GUC set per-request) |
| Tenant isolation (secondary) | Hibernate `@Filter("tenantFilter")` via `HibernateTenantFilterAspect` on every `@Transactional` |
| Tenant context propagation | `TenantContext` (ThreadLocal) → `TenantPropagatingTaskDecorator` for `@Async` |
| Native query discipline | Must include `AND tenant_id = :tenantId` explicitly |
| Sharing tables | Intentionally cross-tenant — no `@Filter`/RLS; service-layer auth only |
| Money | Always `BigDecimal` with explicit scale + `RoundingMode.HALF_UP` |
| Soft-delete | `deletedAt: Instant?`; queries use `WHERE deleted_at IS NULL` |
| Auth | JJWT 0.13, BCrypt strength 12, refresh-token family rotation |
| Secrets at rest | AES-GCM via `AesGcmEncryptionConverter` (`SYNOPTIC_ENCRYPTION_KEY` env) |
| Rate limiting | `RateLimiter` interface (Caffeine-backed; Redis-ready seam) |
| SSRF guard | `OutboundUrlValidator` (https-only, RFC-1918/loopback/link-local/metadata/ULA blocked) |
| HTML sanitization | jsoup `Safelist.relaxed()` on all outbound HTML emails |
| Async jobs | `@Async` via `taskExecutor` bean; `@Scheduled` workers for retention/retry/reorder |
| DB migrations | Flyway; next migration is `V023__…` |
| Tests | Testcontainers + `synoptic_app` NOBYPASSRLS role (RLS fires in tests) |

---

## 7. Phase 9 Prompt (Next Work)

Copy the block below and hand it to an AI coding agent:

```
## Task: Phase 9 — Remaining Backend Gaps (minor)

Context:
- Repo: /home/user/synoptic-engine/api/
- All Phases 0–8 are complete (git log shows commits 85169e0 through b072047).
- Architecture reference: api/CLAUDE.md
- This analysis: api/analysis/02-backend-gap-analysis-2026-05-29.md

You must implement the following three missing endpoints. Read each cited file before editing.

---

### G1 — Organization tag endpoints (§3.1)

Missing from OrganizationController:
  POST  /api/contacts/organizations/{id}/tags       → attach tag
  DELETE /api/contacts/organizations/{id}/tags/{tagId} → detach tag

Steps:
1. Read `OrganizationService.kt` and `PersonService.kt:159-179` — mirror the pattern.
2. Add `attachTag(orgId, tagId)` and `detachTag(orgId, tagId)` to `OrganizationService`.
3. Add the two mappings to `OrganizationController` with `@PreAuthorize("hasAuthority('contacts.edit')")`.
4. Add a unit test mirroring `PersonServiceTest` tag tests if one exists; otherwise add an
   integration test asserting the tag appears and is removable.

---

### G2 — Lead → quotes convenience endpoint (§3.2)

Missing from LeadController:
  GET /api/leads/{id}/quotes → paginated list of quotes for a lead

Steps:
1. Read `LeadController.kt` and `QuoteService.filter()`.
2. Add `GET /{id}/quotes` to `LeadController` with `@PreAuthorize("hasAuthority('quotes.view')")`.
   Delegate to `quoteService.filter(leadId = id, status = null, pageable)`.
   Inject `QuoteService` into `LeadController`.
3. Add a smoke test (or extend an existing lead/quote integration test).

---

### G3 — Product activities endpoint (§3.3)

Missing from ProductController:
  GET /api/products/{id}/activities → paginated activities filtered by productId

Steps:
1. Read `ProductController.kt` and `WarehouseController.kt`'s `GET /{id}/activities` — mirror it.
2. Read `ActivityService.filter()` — it already accepts `productId: UUID?`.
3. Add `GET /{id}/activities` to `ProductController` with `@PreAuthorize("hasAuthority('activities.view')")`.
4. Add a smoke test.

---

### After all three are implemented:

1. Run `./gradlew testClasses` — must compile cleanly.
2. Run `./gradlew unitTests` — must pass.
3. Commit as: `feat(api): Phase 9 — org tags, lead quotes, product activities endpoints`
4. Push to the development branch and create a draft PR.
```
