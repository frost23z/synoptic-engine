# Backend Implementation Plan

> ✅ **STATUS: MVP COMPLETE.** This is the feature inventory / completion record for the
> backend. All planned phases shipped. The next phase is the frontend
> (see [`FRONTEND_PLAN.md`](./FRONTEND_PLAN.md)); the agentic/AI direction is future work
> (see [`FUTURE_AGENTIC_CRM.md`](./FUTURE_AGENTIC_CRM.md)).
>
> Last verified: 2026-06-07 — every controller read directly, cross-checked against Krayin feature
> docs, and the **full Testcontainers integration suite was run end-to-end: 700 tests, 0 failures.**
> Phase 1 completed: 2026-05-29
> Phase 2 completed: 2026-05-30
> Phase 3 completed: 2026-05-31
> Phase 4 (MFA) completed: 2026-05-31
> Next Flyway migration: **V027** (highest applied: `V026__mfa.sql`)
> Build: `./gradlew testClasses` compiles clean; `./gradlew test` (Docker/Testcontainers) is
> **green — 700 tests, 0 failures** (run 2026-06-07).
>
> 2026-06-07 fix: `TenantScopingIntegrationTest` hit pre-`/api`-prefix paths (`/leads`, `/persons`)
> that 500'd on the catch-all handler; corrected to `/api/leads` and `/api/contacts/persons`. This
> test predated the `api.base-path: /api` prefix and had never been executed (the full Docker suite
> had not been run in CI/dev before). The endpoints themselves were always correct — proven by the
> per-module Lead/Person integration tests, which use the prefixed paths and pass.
> Minor pre-existing note: unmapped routes return **500** (caught by the catch-all
> `@ExceptionHandler(Exception::class)`) rather than 404 — low severity, not changed.

---

## Verified Completeness: ~100% (Krayin parity + Phase 2 + Phase 3 enterprise features)

All core CRM, inventory, automation, import/export, sharing, and identity features are
implemented and wired — including AI lead creation (Phase 2). Anything beyond this
(autonomous/agentic AI) is intentionally deferred to `FUTURE_AGENTIC_CRM.md`.

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
| Password policy enforcement | Validator bean in `AuthService` + `UserService` | 2-3h | ✅ |
| MFA / TOTP | TOTP library + `POST /auth/mfa/setup`, `POST /auth/mfa/verify` | 6-8h | ✅ |
| Session management UI | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` | 3-4h | ✅ |
| Login history endpoint | V024 migration + `login_history` table + `GET /auth/login-history` | 2-3h | ✅ |
| Multi-node rate limiter | Swap Caffeine → Redis in `LoginAttemptTracker` (interface already abstracted) | 3-4h | ❌ |
| API keys for integrations | `api_keys` table + static-bearer `JwtAuthFilter` path | 4-6h | ✅ |
| Tenant self-serve signup | Registration flow + email verification + default plan | 4-6h | ✅ (email-verification deferred) |
| Permission change audit | `auditLogService.record()` in `RoleService` create/update/delete | 1-2h | ✅ |
| Folder permissions catalog | `GET /api/mail/folders` returning folders + permission keys | 1h | ✅ |

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

### Folder permissions catalog ✅
- `MailFolderResponse(folder, permissionKey, label)` added to `EmailDtos.kt`
- `GET /api/mail/folders` — static endpoint returning all 6 standard folders (inbox/sent/drafts/trash/spam/outbox) with their permission keys
- Gated on `mail.view`; pure in-memory response, no DB query

### Password policy enforcement ✅
- `PasswordPolicyService` in `shared/config/` — configurable via `synoptic.auth.password-policy.*`
- Properties: `min-length` (default 8), `require-uppercase` (default false), `require-digit` (default false), `require-special` (default false)
- Wired into `AuthService.resetPassword()` and `UserService.updateSelf()` (new-password path)

### MFA / TOTP ✅
- V026 migration: `user_mfa_configs(user_id, totp_secret[AES-GCM], enabled, deleted_at)` + `mfa_backup_codes(user_id, code_hash, used_at)`
- `TotpService` — pure RFC 6238/4226 TOTP + RFC 4648 Base32; no external library
- `MfaService` — `setup(userId)` → generate secret + QR URI, `confirm(userId, code)` → enable + issue 8 backup codes, `verify(userId, code)` → TOTP or backup code, `disable(userId, code)` → soft-delete config
- `JwtTokenProvider.generateMfaChallengeToken()` — short-lived (5 min) JWT with `type: "mfa-challenge"` for 2-step login
- `AuthService.login()` — after credential verify, if MFA enabled: issue challenge token in `TokenResponse(mfaRequired=true, mfaToken=…)` instead of full tokens; login history recorded on `completeMfaLogin()`
- `AuthService.completeMfaLogin(mfaToken, code, clientIp)` — validates challenge token + TOTP/backup code, issues full tokens
- Endpoints: `POST /auth/mfa/setup`, `POST /auth/mfa/confirm`, `POST /auth/mfa/verify` (public), `DELETE /auth/mfa`, `POST /auth/mfa/backup-codes/regenerate`
- Tests: 401 guards, setup returns secret+qrUri, confirm with wrong code returns 400, mfa/verify with invalid token returns 400, login without MFA still returns normal tokens

### Tenant self-serve signup ✅ (2026-06-07)
- Public `POST /auth/register` (permit-listed in `SecurityConfig`) — a new company registers itself
  with `{ companyName, email, password }`, gets its first **admin** user, and is **auto-logged-in**
  (returns the normal `TokenResponse`). This is how a company onboards without an existing admin.
- `AuthService.register()` — rate-limited per (email, IP) via the new `RegistrationAttemptTracker`
  (mirrors `ForgotPasswordAttemptTracker`); enforces the password policy; rejects an already-used
  email (email is globally unique → 409); then delegates to `TenantApi.registerSelfService()` and
  logs the admin in.
- `TenantProvisioningService.registerSelfService()` — derives a **unique slug** from the company
  name (slugify + random suffix on collision) and reuses the existing `provision()` seeding path
  (default roles + admin user + CRM defaults via `TenantProvisionedEvent`).
- Tests: `RegistrationIntegrationTest` — 201 + tokens, new admin is scoped to its own tenant
  (`GET /api/leads` → 200), duplicate email → 409, short password → 422, blank company → 422,
  endpoint is public (never 401).
- **Deferred:** email verification + plan selection (auto-login is the MVP onboarding path).

### Routing: unmapped paths return 404 (2026-06-07)
- `GlobalExceptionHandler` now has an `@ExceptionHandler(NoResourceFoundException)` → **404**.
  Previously an unknown path fell through to the catch-all `Exception` handler and surfaced as a
  misleading **500**. Test: `TenantScopingIntegrationTest.unmapped route returns 404 not 500`.

### API keys for integrations ✅
- V025 migration: `api_keys(id, tenant_id, user_id, name, key_hash, key_prefix, created_at, expires_at, revoked_at, last_used_at)`
- `ApiKey` entity (NOT extending BaseEntity), `ApiKeyRepository` with hand-written JPQL
- `ApiKeyService` — `create()` returns raw key once, `list()` returns prefix only, `revoke()`, `authenticateByKey()`
- `JwtAuthFilter` detects `sk_` prefix → calls `apiKeyService.authenticateByKey()` → sets SecurityContext/TenantContext/ActorContext
- `POST /auth/api-keys` → `ApiKeyCreateResponse` (includes raw key once only)
- `GET /auth/api-keys` → `List<ApiKeyResponse>` (prefix + metadata, no raw key)
- `DELETE /auth/api-keys/{keyId}` → revoke
- Tests: 401 guards, create/list/revoke flow, API key bearer token auth

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

## Next migration: V027
