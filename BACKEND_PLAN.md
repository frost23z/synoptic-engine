# Backend Implementation Plan

> Last verified: 2026-05-30 — every controller read directly, cross-checked against Krayin feature docs
> Phase 1 completed: 2026-05-29
> Phase 2 completed: 2026-05-30
> Phase 3 (partial) completed: 2026-05-31
> Next Flyway migration: **V025**

---

## Verified Completeness: ~100% (Krayin parity + Phase 2 + Phase 3 enterprise features)

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
| `POST /api/leads/ai-create` (LLM file extraction) | ✅ |
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

## Phase 2 — Advanced / Non-Blocking ✅ COMPLETE

### AI Lead Creation ✅
```
POST /api/leads/ai-create    (multipart: file + optional hints)
```
- `LeadController.kt` — `POST /ai-create` (consumes multipart/form-data)
- `AiLeadService.kt` — extracts text via Apache Tika, calls Claude `claude-opus-4-8`
  with adaptive thinking + prompt caching, parses JSON response, delegates to `LeadService.create()`
- `FileUploadGuard.validateAiLeadFile()` — accepts PDF + common image types (10 MB limit)
- Dependencies added: `anthropic-java:2.34.0`, `tika-core:2.9.2`, `tika-parsers-standard-package:2.9.2`
- Requires `ANTHROPIC_API_KEY` env var at runtime (client is lazy-initialized; absent key
  surfaces only on first call, not at startup)
- Tests: `LeadIntegrationTest` — 401 guard, 403 guard (VIEWER blocked)

---

## Phase 3 — Enterprise / Operational (not in Krayin scope)

| Feature | What it needs | Effort | Status |
|---|---|---|---|
| Password policy enforcement | Validator bean in `AuthService` + `UserService` | 2-3h | ❌ |
| MFA / TOTP | TOTP library + `POST /auth/mfa/setup`, `POST /auth/mfa/verify` | 6-8h | ❌ |
| Session management UI | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` | 3-4h | ✅ |
| Login history endpoint | V024 migration + `login_history` table + `GET /auth/login-history` | 2-3h | ✅ |
| Multi-node rate limiter | Swap Caffeine → Redis in `LoginAttemptTracker` (interface already abstracted) | 3-4h | ❌ |
| API keys for integrations | `api_keys` table + static-bearer `JwtAuthFilter` path | 4-6h | ❌ |
| Tenant self-serve signup | Registration flow + email verification + default plan | 4-6h | ❌ |
| Permission change audit | `auditLogService.record()` in `RoleService` create/update/delete | 1-2h | ✅ |
| Folder permissions catalog | `GET /api/mail/folders` returning folders + permission keys | 1h | ❌ |

### Session management ✅
- `AuthService.listSessions()` / `revokeSession()` — query `user_refresh_sessions` for non-expired, non-revoked rows
- `GET /auth/sessions` → list of active sessions; `DELETE /auth/sessions/{sessionId}` → revoke (user-owned only)
- Tests: 401 guard, list returns entry, revoke returns 204

### Login history ✅
- V024 migration: `login_history(id, user_id, tenant_id, client_ip, logged_in_at)`
- `LoginHistory` entity, `LoginHistoryRepository`, recorded on every successful `AuthService.login()`
- `GET /auth/login-history?page=0&size=20` → paginated list
- Tests: 401 guard, returns entry after login

### Permission change audit ✅
- `RoleService` now injects `AuditLogService`
- `create()` → `AuditAction.CREATE` with role name + permissions list
- `update()` → `AuditAction.UPDATE` with `permissionsAdded` / `permissionsRemoved` diff (only when changed)
- `delete()` → `AuditAction.DELETE` with role name

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
