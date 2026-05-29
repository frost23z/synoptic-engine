# Backend Implementation Plan

> Last verified: 2026-05-29 — every controller read directly, cross-checked against Krayin feature docs
> Phase 1 completed: 2026-05-29
> Next Flyway migration: **V024**

---

## Verified Completeness: ~98%

All core CRM, inventory, automation, import/export, sharing, and identity features are
implemented and wired. The remaining ~2% is one advanced feature (AI lead creation) and
enterprise features not in the Krayin scope.

---

## Complete Feature Inventory (verified from source)

### Auth
| Endpoint | Status |
|---|---|
| `POST /auth/login` | ✅ |
| `POST /auth/refresh` | ✅ |
| `GET /auth/me` | ✅ read-only |
| `POST /auth/logout` | ✅ |
| `POST /auth/logout-all` | ✅ |
| `POST /auth/forgot-password` | ✅ rate-limited |
| `POST /auth/reset-password` | ✅ |
| `PUT /auth/me` (self-edit name/phone/password) | ✅ |

### Leads (all verified)
`GET/POST /api/leads` · `GET/PUT/DELETE /api/leads/{id}` · `PATCH /{id}/stage` ·
`POST /mass-update` · `POST /mass-destroy` · `GET /search` · `GET /rotten` ·
`GET /kanban` · `POST /leads/{id}/convert` ·
`GET/POST/DELETE /{id}/products` · `POST/DELETE /{id}/tags` ·
`GET/POST/DELETE /{id}/emails` · `GET /{id}/activities` · `GET /{id}/quotes` ·
`GET /api/lead-sources` CRUD · `GET /api/lead-types` CRUD ·
`GET/POST/DELETE /export`

| Endpoint | Status |
|---|---|
| Lead CRUD + stage move + mass ops | ✅ |
| Kanban board | ✅ |
| Rotten leads | ✅ |
| Lead convert to contact | ✅ |
| Lead products sub-resource | ✅ |
| Lead tags sub-resource | ✅ |
| Lead emails sub-resource | ✅ |
| Lead activities sub-resource | ✅ |
| Lead quotes sub-resource | ✅ |
| CSV export | ✅ |
| `GET /api/leads/kanban/lookup` (filter dropdown data) | ✅ |
| `POST /api/leads/ai-create` (LLM file extraction) | ❌ **MISSING** (Phase 2) |
| `PATCH /api/leads/{id}/attributes` (custom-attrs-only update) | ✅ |

### Contacts, Activities, Email, Quotes, Products, Warehouses (all verified)
All CRUD, tags, activities sub-resources, search, mass ops, file uploads, calendar,
overlap check, PDF generation, send-mail, duplicate — **all complete**.

### Pipelines & Stages
`GET/POST/PUT/DELETE /api/pipelines` · stage CRUD · `PUT/PATCH /stages/reorder` —
**all complete including reorder**.

### Settings
| Feature | Status |
|---|---|
| Attributes (EAV) — full CRUD + options + lookup + unique check | ✅ |
| Workflows — CRUD + run history | ✅ |
| WorkflowEngine — auto-executes via `@EventListener` on `DomainEvent` | ✅ wired |
| Webhooks — CRUD + delivery history | ✅ |
| Webhook test delivery | ✅ `POST /{id}/test` |
| Email templates — CRUD + `POST /{id}/render` with `{{var}}` | ✅ |
| Marketing events + campaigns + `POST /{id}/execute` | ✅ |
| Marketing scheduled send worker (retry/backoff) | ✅ |
| Web forms — CRUD + CAPTCHA + public submit | ✅ |
| CSV import pipeline (upload → validate → link → index → start → errors) | ✅ |
| CSV export (leads, persons, organizations, products) | ✅ |
| System config CRUD | ✅ |

### Identity & Sharing
Users/Roles/Groups/Permissions/Tenants · Cross-tenant relationships/shares/policies/audit
— **all complete**.

### Inventory
Warehouse CRUD + locations + tags + activities · Product inventory per warehouse ·
Reserve/release movements · Low-stock check · Transfer orders (create/dispatch/receive/cancel)
· Reorder worker — **all complete**.

### Dashboard & DataGrid
8 dashboard stat types · DataGrid saved filters — **complete**.

---

## Phase 1 — Krayin Parity Endpoints ✅ COMPLETE

All three endpoints are implemented, wired, and covered by integration tests.

### 1. Account Self-Edit `PUT /auth/me` ✅
- `AuthController.kt` — `PUT /auth/me`
- `UserService.kt` — `updateSelf()` validates current password before allowing change
- `IdentityApi.kt` — interface method wired to `UserService`
- Tests: `AuthIntegrationTest` — name/phone update, password change, wrong-password rejection

### 2. Kanban Filter Lookup `GET /api/leads/kanban/lookup` ✅
- `LeadController.kt` — `GET /kanban/lookup`
- `LeadService.kt` — `kanbanLookup(pipelineId)` returns users, sources, types, pipeline stages
- Tests: `LeadIntegrationTest` — structure check, 401 guard

### 3. Lead Custom-Attribute Partial Update `PATCH /api/leads/{id}/attributes` ✅
- `LeadController.kt` — `PATCH /{id}/attributes`
- `LeadService.kt` — `updateAttributes()` validates lead exists, delegates to `EntityAttributePort`
- Tests: `LeadIntegrationTest` — value set, unknown lead 404, VIEWER 403

---

## Phase 2 — Advanced / Non-Blocking (future sessions)

### AI Lead Creation (medium effort — requires LLM integration)
```
POST /api/leads/ai-create    (multipart: file + optional hints)
```
- Parse PDF/image → extract lead fields via Claude API
- Create lead, return `LeadResponse`
- Requires: `@anthropic-ai/sdk` equivalent for Kotlin (`anthropic-sdk-java`), prompt
  engineering, file parsing (Apache Tika for PDF/image text extraction)
- **Effort:** 8-12h

---

## Phase 3 — Enterprise / Operational (not in Krayin scope)

| Feature | What it needs | Effort |
|---|---|---|
| Password policy enforcement | Validator bean in `AuthService` + `UserService` | 2-3h |
| MFA / TOTP | TOTP library + `POST /auth/mfa/setup`, `POST /auth/mfa/verify` | 6-8h |
| Session management UI | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` — `RefreshSession` table already exists | 3-4h |
| Login history endpoint | `login_history` table + populate in `AuthService.login()` | 2-3h |
| Multi-node rate limiter | Swap Caffeine → Redis in `LoginAttemptTracker` (interface already abstracted) | 3-4h |
| API keys for integrations | `api_keys` table + static-bearer `JwtAuthFilter` path | 4-6h |
| Tenant self-serve signup | Registration flow + email verification + default plan | 4-6h |
| Permission change audit | Add `auditLogService.record()` in `RoleService.update()` | 1-2h |
| Folder permissions catalog | `GET /api/mail/folders` returning folders + permission keys | 1h |

---

## Architecture Rules (must follow — from `api/CLAUDE.md`)

- Money = `BigDecimal` always
- Tenant isolation = RLS + Hibernate `@Filter` (two layers, both required)
- Sharing tables = intentionally cross-tenant, no `@Filter` on them
- All entities extend `BaseEntity`
- Soft-delete everywhere (`deletedAt`)
- Repositories = hand-written `@Query` only — no `JpaSpecificationExecutor`
- Every `@RequestBody` → `@Valid`
- Every endpoint → `@PreAuthorize`
- Class-level `@Transactional(readOnly = true)`, explicit `@Transactional` on writes

## Next migration: V024
