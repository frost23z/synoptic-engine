# Backend Implementation Plan

> Generated: 2026-05-29  
> Based on direct codebase audit (not automated analysis)

---

## Actual Backend Status

**The backend is ~98% complete.** The automated analysis earlier significantly overstated the gaps.
Most things it flagged as "missing" are already fully implemented.

### Confirmed Complete

| Area | Status |
|---|---|
| Lead CRUD + stage move + mass ops | ✅ |
| Lead tag / email / product / activity / quote sub-resources | ✅ |
| Lead convert, search, kanban, rotten leads | ✅ |
| Person CRUD + tags + activities + merge | ✅ |
| Organization CRUD + tags + activities + mass-destroy | ✅ |
| Activity CRUD + calendar + check-overlap + file upload + participants | ✅ |
| Email CRUD + send/reply/forward + inbound parse + signature verification | ✅ |
| Quote CRUD + PDF + send-email + duplicate | ✅ |
| Product CRUD + tags + activities + warehouse inventory | ✅ |
| Warehouse CRUD + locations + tags + activities + mass-destroy | ✅ |
| Pipeline CRUD + stage CRUD + stage reorder (PUT + PATCH) | ✅ |
| LeadSource + LeadType CRUD | ✅ |
| Tag CRUD | ✅ |
| Attribute EAV system | ✅ |
| Users / Roles / Groups / Permissions CRUD | ✅ |
| WorkflowEngine — wired via `@EventListener`, runs async on DomainEvents | ✅ |
| Webhook CRUD + delivery runs + HMAC signing | ✅ |
| Email template CRUD + `POST /{id}/render` with `{{variable}}` interpolation | ✅ |
| Marketing events + campaigns + scheduled send worker with retry/backoff | ✅ |
| Campaign execute endpoint | ✅ |
| Data import (upload → validate → link → indexData → start) + error CSV download | ✅ |
| CSV export (persons, organizations, leads, products) | ✅ |
| Inventory movements (stock, reserve, release, low-stock) | ✅ |
| Transfer orders (list, create, dispatch, receive, cancel) | ✅ |
| Reorder point worker | ✅ |
| Web forms + CAPTCHA + public submission | ✅ |
| Multi-tenancy: RLS + Hibernate `@Filter` | ✅ |
| Cross-tenant sharing: relationships, policies, record shares, materialization | ✅ |
| Dashboard stats | ✅ |
| DataGrid saved filters | ✅ |
| JWT auth + refresh + password reset + rate limiting | ✅ |
| AES-GCM encryption for secrets | ✅ |
| SSRF protection, file validation, CORS | ✅ |
| Inbound mail webhook — HMAC signature + replay-window verification | ✅ |

---

## Remaining Gaps

### Phase 1 — This Session (30 min)

**1. Webhook test delivery endpoint** ← _the only confirmed missing endpoint_

```
POST /api/settings/webhooks/{id}/test
```

- Fires a synthetic `webhook.test` event at the configured URL
- Returns a `WebhookDeliveryRunResponse` immediately (synchronous, not async)
- Records the delivery run in `webhook_delivery_runs` like a real delivery
- Validates the URL via `OutboundUrlValidator` before firing

**Status:** `AutomationService`, `WebhookDispatcher`, and all supporting infra exist.
Only the controller route and a sync dispatch method are missing.

---

### Phase 2 — Next Session (complex features)

**2. AI Lead Creation**

```
POST /api/leads/ai-create
```

- Accepts file upload (PDF, image, text) or raw text
- Calls an LLM (e.g., Claude API) to extract lead fields
- Creates the lead, returns `LeadResponse`
- Requires: LLM client setup, prompt engineering, file parsing

**Effort:** 6-8h. Needs Anthropic SDK dependency + `AnthropicConfig` bean.

**3. Password Policy Enforcement**

- Minimum 8 chars, at least one uppercase/digit/special
- Enforce on: registration, reset-password, user-create, user-update
- Requires: `PasswordPolicyService` + validation at `AuthService` + `UserService`

**Effort:** 2-3h.

---

### Phase 3 — Future Sessions (operational / enterprise)

| Feature | Effort | Notes |
|---|---|---|
| MFA / TOTP | 6-8h | TOTP library + QR code endpoint + login second-factor |
| In-app notification system | 8-12h | New module, SSE or polling, notification table |
| Multi-node rate limiter (Redis) | 3-4h | Swap Caffeine → Redis for `LoginAttemptTracker` |
| API keys for integrations | 4-6h | Static bearer token + `api_keys` table |
| Tenant provisioning self-serve | 4-6h | Registration flow, email verify, default plan |
| Tenant usage limits / quotas | 6-8h | Per-tier record count limits, storage tracking |
| Session management UI | 2-3h | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` |
| Login history endpoint | 2-3h | `login_history` table populated in `AuthService.login()` |
| Permission change audit | 1-2h | Audit log in `RoleService.update()` |

---

## Architecture Rules (do not deviate)

From `api/CLAUDE.md`:

- Money = `BigDecimal` always
- Tenant isolation = RLS + Hibernate `@Filter` (two-layer)
- Sharing tables = intentionally cross-tenant, no `@Filter`
- All entities extend `BaseEntity`
- Soft-delete everywhere (`deletedAt`)
- Repositories = hand-written `@Query` only, no `JpaSpecificationExecutor`
- Every `@RequestBody` gets `@Valid`
- Every endpoint gets `@PreAuthorize`
- Class-level `@Transactional(readOnly = true)`, override with `@Transactional` on writes

## Next migration number

`V024__…`
