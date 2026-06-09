# Synoptic Engine — Production-Readiness Audit Findings

> Multi-tenant CRM MVP · Kotlin + Spring Modulith (API) + Nuxt 4 (Web) · PostgreSQL (RLS + Hibernate `@Filter`)
> Audit date: 2026-06-09 · Scope: gaps/risks/inconsistencies only (no new features, no redesign beyond fixing critical isolation flaws)

This document is structured so each finding is self-contained and independently fixable in a fresh session:
**ID · Severity · Area · What · Why it's dangerous · Where (file:line) · Minimal fix · `- [ ]` status.**

See the **[Coverage Map](#coverage-map)** at the bottom for exactly what was read-in-full vs. sampled vs. not-yet-checked, and **[Verified Safe / Done Well](#verified-safe--done-well)** so you don't re-investigate things that are fine.

> **Scope note (2026-06-09):** this audit measures *production multi-tenant SaaS* readiness. For
> *final-year defense showcase* readiness — a single-tenant supervised demo — see
> **[`SHOWCASE_READINESS.md`](./SHOWCASE_READINESS.md)**: the app was driven end-to-end across all 40
> pages (40/40 render), seeded with demo data, and the one functional bug found ([FE-5](#fe-5)) was fixed.

---

## Priority Index

| ID | Sev | Area | Title |
|----|-----|------|-------|
| [MT-1](#mt-1) | 🔴 Critical | Multi-tenancy | Postgres RLS is inert in the production config (no FORCE / no role separation) |
| [MT-2](#mt-2) | 🔴 Critical | Multi-tenancy | Cross-tenant mass mutation via tenant-predicate-less bulk JPQL (Lead/Person/Org) |
| [MT-3](#mt-3) | 🟠 High | Sharing | Cross-tenant sharing data-plane is unwired; code comments claim it works |
| [SEC-1](#sec-1) | 🟠 High | Auth | MFA bypass: `/auth/mfa/verify` is unauthenticated + no rate limit + replayable challenge |
| [SEC-2](#sec-2) | 🟠 High | Auth | TOTP code replay within window + non-constant-time comparison |
| [SEC-3](#sec-3) | 🟠 High | Auth (Web) | Tokens in non-httpOnly cookies + logout never revokes server session |
| [SEC-4](#sec-4) | 🟠 High | Auth (Web) | MFA-enabled users are locked out of the web app |
| [BE-1](#be-1) | 🟠 High | Backend | Async email always recorded `SENT`; retries are dead code; `@Transactional` no-op |
| [BE-2](#be-2) | 🟠 High | Backend | Unbounded page size on most list endpoints (DoS/OOM) |
| [OPS-1](#ops-1) | 🟠 High | DevOps | Public Swagger in prod + credentialed wildcard CORS + no security headers/HSTS |
| [OPS-2](#ops-2) | 🟠 High | DevOps | No production datasource profile / no fail-fast (ties to MT-1 role) |
| [BE-3](#be-3) | 🟠 High | Backend | Whole-CSV import in one `@Async @Transactional` (pool starvation, partial-progress loss) |
| [SEC-5](#sec-5) | 🟡 Medium | Auth | Password-reset row keyed by email globally → griefing DoS + no user/tenant binding |
| [SEC-6](#sec-6) | 🟡 Medium | Auth | `forgot-password` timing/latency enumeration oracle |
| [SEC-7](#sec-7) | 🟡 Medium | Auth | Dummy bcrypt cost 10 vs real cost 12 reopens the login timing oracle |
| [SEC-8](#sec-8) | 🟡 Medium | Auth | JWT has no `iss`/`aud`; token kinds separated only by a self-asserted claim; no secret-entropy check |
| [SEC-9](#sec-9) | 🟡 Medium | Auth | Weak password-policy defaults (min 8, no class requirements) |
| [SEC-10](#sec-10) | 🟡 Medium | Auth | In-memory rate limiters (multi-node ineffective) + `X-Forwarded-For` lockout dodge |
| [MT-4](#mt-4) | 🟡 Medium | Multi-tenancy | `users.email` globally unique → no multi-tenant membership + cross-tenant existence oracle |
| [BE-4](#be-4) | 🟡 Medium | Backend | OSIV enabled and the tenant filter is coupled to it |
| [BE-5](#be-5) | 🟡 Medium | Backend | Negative inventory quantity accepted |
| [BE-6](#be-6) | 🟡 Medium | Backend | N+1 on list endpoints (batch-mitigated) + `getLowStock` unpaged |
| [OPS-3](#ops-3) | 🟡 Medium | DevOps | No connection-pool sizing, structured logging, or correlation IDs |
| [OPS-4](#ops-4) | 🟡 Medium | DevOps | Encryption converter can silently fall back to plaintext; dead JWT default-marker guard |
| [FE-1](#fe-1) | 🟡 Medium | Frontend | Client-side route authorization is cosmetic only |
| [FE-2](#fe-2) | 🟡 Medium | Frontend | SSR bakes authenticated data into the hydration payload |
| [FE-3](#fe-3) | 🟡 Medium | Frontend | `useApi` no retry after refresh + stale cross-user state after logout |
| [INJ-1](#inj-1) | 🟡 Medium | Injection | CSV formula injection on all exports |
| [INJ-2](#inj-2) | 🟡 Medium | Injection | Hand-rolled CSV builder with no escaping (`AttributeService.downloadCsv`) |
| [INJ-3](#inj-3) | 🟡 Medium | Upload | File upload validated by client-declared Content-Type only (SVG allowed) |
| [API-1](#api-1) | 🟡 Medium | API | `ConstraintViolationException` → 422 instead of 400 |
| [INJ-4](#inj-4) | 🔵 Minor | SSRF | `OutboundUrlValidator` misses `0.0.0.0`/`::` and IPv4-mapped IPv6 |
| [INJ-5](#inj-5) | 🔵 Minor | Injection | Captcha verifier concatenates unescaped user token into HTTP body |
| [INJ-6](#inj-6) | 🔵 Minor | XSS | Email-template `preview` returns interpolated-but-unsanitized HTML |
| [BE-7](#be-7) | 🔵 Minor | API | `getStock` returns bare 400 bypassing the ProblemDetail shape |
| [BE-8](#be-8) | 🔵 Minor | Backend | Quote/Product/Warehouse/Activity/Email `massDestroy` use per-id loops |
| [DATA-1](#data-1) | 🔵 Minor | Data | `EmailRepository.reassignPerson` bulk UPDATE has no tenant predicate |
| [DATA-2](#data-2) | 🔵 Minor | Sharing | Share-policy filter treats missing field as `""` → over-shares owner's own records |
| [SEC-11](#sec-11) | 🔵 Minor | Auth | API-key hash compare not constant-time; no server-side pepper |
| [SEC-12](#sec-12) | 🔵 Minor | Auth | `login`/`completeMfaLogin` not `@Transactional` (non-atomic multi-write) |
| [OPS-5](#ops-5) | 🔵 Minor | DevOps | Dockerfile root/unpinned/no healthcheck; 200MB multipart; `workflow_dispatch` bypasses CI gate |
| [CQ-1](#cq-1) | 🔵 Minor | Code quality | Doc drift: comments reference non-existent `V040`, `sharedResourceFinder`, "Sprint 2b" |
| [FE-4](#fe-4) | 🔵 Minor | Frontend | Placeholder avatar external request; `reset-password` `setTimeout` without cleanup |
| [BE-9](#be-9) | 🔵 Minor | Backend | `WorkflowEngine` workflow-level failure is silently logged, not persisted |
| [FE-5](#fe-5) | ✅ Fixed | Frontend | Leads page issued GET `/pipelines/{id}/stages` → 405 (path is POST-only) |
| [BE-10](#be-10) | 🟡 Medium | Inventory | Soft-deleted product SKUs stay reserved (`uq_products_tenant_sku` not partial) → recreate 500s |
| [BE-11](#be-11) | 🟡 Medium | Inventory | Low-stock / stock totals sum inventory across soft-deleted warehouses |
| [FE-6](#fe-6) | ✅ Fixed | Frontend | `USelect` items with empty-string `value` crash the page (NuxtUI 4) — Stock, Shared-with-me, Marketing |
| [FE-7](#fe-7) | ✅ Fixed | Frontend | Users settings page used `usePaginatedList` against an array endpoint → always showed 0 users |
| [FE-8](#fe-8) | ✅ Fixed | Frontend | Cross-tenant names rendered as UUIDs — tenant directory gated on client-only auth at SSR |

---

## 🔴 Critical

### MT-1
**RLS is inert in the production deployment — the "authoritative" isolation layer never fires.** `- [ ]`

- **What.** The whole design (documented in `RlsTenantGucAspect.kt`, `Lead.kt:28`, `LeadRepository.kt:179`) treats Postgres RLS as the authoritative boundary and the Hibernate `@Filter` as secondary. But RLS only constrains a role that is *subject* to it. Postgres exempts the **table owner** unless `ALTER TABLE … FORCE ROW LEVEL SECURITY` is set, and exempts any `BYPASSRLS`/superuser role unconditionally.
  - `FORCE ROW LEVEL SECURITY` appears **nowhere** in `api/src/main/resources/db/migration/`.
  - The `synoptic_app` NOBYPASSRLS role and `SET ROLE synoptic_app` exist **only in tests** (`api/src/test/resources/db/test/init-non-superuser.sql:8`, `api/src/test/resources/application-test.yaml:24-26`). The RLS test passes only because Testcontainers' superuser owns the tables while the app connects as a separate non-owner role (`api/src/test/kotlin/com/synopticengine/api/RlsTwoTenantIsolationTest.kt:24-38`).
  - There is **no `application-prod.yaml`**, no `spring.datasource.*`, and no `connection-init-sql` on the production path. A standard deployment uses one DB user for Flyway + runtime → that user owns the tables it migrated → RLS is skipped for all app queries.
- **Why dangerous.** Defense-in-depth collapses to a single layer (Hibernate `@Filter`), which by design does **not** apply to (a) native SQL without an explicit predicate, (b) bulk JPQL `UPDATE`/`DELETE`, or (c) any null-`TenantContext` path. The test suite **cannot** detect this because tests run under a correctly-separated role; production isolation is therefore unverified by CI. The 4 auth tables (`api_keys`, `user_refresh_sessions`, `login_history`, `user_password_resets`) aren't even in the RLS migrations and have no `@Filter` — they rely 100% on service-layer predicates (currently correct, but with zero backstop).
- **Where.** `db/migration/V007__sharing_and_rls.sql`, `V011__rls_baseline.sql` (no FORCE); absence of `application-prod.yaml`; contrast `application-test.yaml` + `init-non-superuser.sql`.
- **Fix (minimal).**
  1. Migration: `ALTER TABLE <every RLS table> FORCE ROW LEVEL SECURITY;`
  2. Provision the runtime role as **NOBYPASSRLS, non-superuser, non-owner** (tables owned by a separate migration role); set `spring.datasource.hikari.connection-init-sql: SET ROLE synoptic_app` in a prod profile — i.e. replicate what the test harness already proves works.
  3. Add a boot-time self-check that performs a 2-tenant read as the runtime role and fails startup if cross-tenant rows are visible.
  4. Extend RLS policies (or accept `@Filter`-only) to the 4 auth tables and document the decision.

### MT-2
**Cross-tenant mass mutation via bulk `@Modifying` JPQL with no tenant predicate.** `- [ ]`

- **What.** Bulk soft-delete/update JPQL filters only by `id IN :ids`, with **no `tenant_id`**. Hibernate `@Filter` is **not applied to bulk HQL DML**, so neither Hibernate layer scopes these. The `ids` come straight from the request body.
  - `LeadRepository.kt:455-492` (`bulkSoftDelete`, `bulkSetUserId`, `bulkSetStageId`, `bulkSetStatus`) → `LeadController.kt:161-177` (`/leads/mass-update`, `/leads/mass-destroy`).
  - `PersonRepository.kt:103` (`bulkSoftDelete`) → `PersonController.kt:106` (`/persons/mass-destroy`).
  - `OrganizationRepository.kt:87` (`bulkSoftDelete`) → `OrganizationController.kt:100` (`/organizations/mass-destroy`); `OrganizationService.massDestroy` does no upstream ownership check at all.
- **Why dangerous.** The only guard is RLS — which per **MT-1** is very likely off in production. A user in tenant A with `leads.delete` can `POST /organizations/mass-destroy {"ids":[<tenant-B org UUIDs>]}` and soft-delete another tenant's records. Even where RLS is on, the test suite (BYPASSRLS) can never catch a regression here. The team already applied explicit `tenant_id` predicates to the *native dashboard* queries for exactly this reason (`LeadRepository.kt:179-188`) but missed the bulk mutators.
- **Where.** Listed above. Audit every `@Modifying` query for the same pattern.
- **Fix.** Add `AND <alias>.tenantId = :tenantId` to every bulk mutator and pass the tenant from `TenantContext.get()`. (`UserRepository.reactivateByIds` already does this correctly — use it as the template.) Independent of, and complementary to, MT-1.

---

## 🟠 High

### MT-3
**Cross-tenant resource-sharing data-plane is unwired; the code claims otherwise.** `- [ ]`

- **What.** The sharing control plane (relationships → policies → `resource_visibility` materialization → record shares → audit) is well-built and correctly authorized. **But shared records are never served or written through the CRM endpoints:**
  - `Lead.kt:26-27` claims *"LeadService.findAll merges own-tenant + sharedResourceFinder.idsFor(LEADS)"* — `sharedResourceFinder` exists nowhere; `LeadService.findAll` (`LeadService.kt:64-71`) does only intra-tenant owner/group scoping.
  - `SharingApi.effectiveAccess`/`visibleIds` are consulted for reshare authorization but never in the data-serving path.
  - `RecordShareService.assertCanWrite/assertCanDelete` ("call from cross-tenant mutation paths") have **zero callers** outside the sharing package.
  - `CrossTenantVisibilityIntegrationTest.kt:18-22` admits the feature is visibility-layer only and not surfaced through `/api/leads`.
- **Why dangerous.** It fails closed (no leak), so this is a **correctness/consistency** risk, not a vulnerability. But a flagship advertised capability is non-functional end-to-end, and the code actively mis-describes its own behavior — a future engineer may relax the filter based on a false premise.
- **Where.** `Lead.kt:26-29`, `LeadService.kt:64-103`, `RecordShareService.kt:139-180` (dead guards), `CrossTenantVisibilityIntegrationTest.kt:18-22`.
- **Fix (no feature build).** Correct the misleading comments to state the real status; mark the sharing data-plane as not-production (feature flag/docs); remove or clearly annotate the dead `assertCanWrite/Delete` guards. (If you *do* wire it later, route reads through `effectiveAccess`/`visibleIds` and gate writes with the existing guards.)

### SEC-1
**MFA bypass: `/auth/mfa/verify` is `permitAll`, has no rate limiting, and the challenge token is replayable.** `- [ ]`

- **What.** After a correct password with MFA enabled, the login response returns a 5-minute `mfa-challenge` JWT in the body (`AuthService.kt:100-112`). `completeMfaLogin` (`AuthService.kt:124-146`) validates the token + `mfaService.verify(userId, code)` but: the challenge token has **no jti and is not invalidated on use** (replayable for 5 min), and `completeMfaLogin` does **not** call any attempt tracker (contrast the password path at `AuthService.kt:78`). `SecurityConfig.kt:64` makes the endpoint `permitAll`.
- **Why dangerous.** An attacker holding a valid challenge token can brute-force the 6-digit TOTP (±1 step ⇒ ~3M acceptable at any instant) with no lockout. Defeats the entire second factor once the password is known.
- **Where.** `AuthService.kt:124-146`, `JwtTokenProvider.kt:82-94`, `SecurityConfig.kt:64`.
- **Fix.** Make the challenge single-use (persist/Redis jti consumed on first verify) and apply a per-user attempt tracker to `completeMfaLogin` that locks the challenge after a few bad codes.

### SEC-2
**TOTP code replay within its window + non-constant-time comparison.** `- [ ]`

- **What.** `TotpService.kt:44` compares with Kotlin `==` (content equality, not constant-time); backup codes likewise at `MfaService.kt:145`. `MfaService.verify` (`:78-87`) accepts any code within ±1 step **every time** — no "last consumed step" tracking, so a TOTP code is replayable for ~90s. (Backup codes *are* single-use — `MfaBackupCode.usedAt`.)
- **Why dangerous.** Weak timing side-channel on the code/hash; more importantly, replay + no rate-limit (SEC-1) makes online guessing feasible.
- **Where.** `TotpService.kt:44`, `MfaService.kt:78-87,145`.
- **Fix.** Use `MessageDigest.isEqual`/constant-time comparator; record the last consumed time-step per `MfaConfig` and reject reuse of the same/earlier step.

### SEC-3
**Auth tokens stored in JS-readable cookies; logout never revokes the server session.** `- [ ]`

- **What.** Access **and** 7-day refresh tokens live in non-`httpOnly`, non-`secure` cookies read by JS (`web/app/stores/auth.ts:6-16`, used in `useApi.ts:14`). `logout()` (`auth.ts:96-101`) clears cookies only — it never calls `POST /api/auth/logout`, which exists and revokes the refresh session (`AuthService.kt:296-310`).
- **Why dangerous.** Any XSS exfiltrates a long-lived refresh token; because logout is cosmetic, that token stays valid server-side up to 7 days. Missing `secure` means tokens transit in clear if ever served over HTTP.
- **Where.** `web/app/stores/auth.ts:6-16,96-101`.
- **Fix.** Call `POST /api/auth/logout` (best-effort) before clearing client state; add `secure:true`. Durable fix: move token custody to backend-set `httpOnly+secure` cookies via a Nitro proxy (`web/server/` does not exist yet).

### SEC-4
**MFA-enabled users are locked out of the web app.** `- [ ]`

- **What.** When MFA is on, the backend returns `200` with empty tokens + `mfaRequired:true` + `mfaToken` (`AuthService.kt:99-112`). Frontend `login()` calls `setSession(data)` unconditionally, stores empty tokens, discards `mfaToken`, and there is **no MFA UI** (`web/app/stores/auth.ts:34-41`; `TokenResponse` type omits the MFA fields).
- **Why dangerous.** Any user who enables MFA can never log in again (silent bounce to `/login`). A backend security feature is dead on the frontend.
- **Where.** `web/app/stores/auth.ts:34-41`, `web/app/types/auth.ts`.
- **Fix.** Branch on `data.mfaRequired`: don't set a session; route to an MFA-code step POSTing `mfaToken`+`code` to `/api/auth/mfa/verify`, then `setSession`. Add the missing type fields.

### BE-1
**Async email always recorded `SENT`; retry path is dead code; one `@Transactional` is a no-op.** `- [ ]`

- **What.** `MailSenderService.sendEmail/sendHtmlEmail` are `@Async` (fire-and-forget). Callers set status `SENT` unconditionally after the call returns immediately; the `try/catch` can never observe the SMTP exception (`EmailDeliveryService.kt:18-42`, `MarketingSendWorker.kt:56-70`) — so the marketing retry/backoff `catch` is unreachable. Compounded: `MarketingSendWorker.processJob` is `private` + self-invoked, so its `@Transactional` doesn't apply (`MarketingSendWorker.kt:48-72`).
- **Why dangerous.** Failed/bounced emails are reported as delivered; campaigns silently never retry; send-state bookkeeping is non-transactional and can tear.
- **Where.** `MailSenderService.kt:17,37`, `EmailDeliveryService.kt:18-42`, `MarketingSendWorker.kt:48-72`.
- **Fix.** Make `sendEmail` return synchronously (move the `@Async` boundary up to the worker) so status reflects the real result; move `processJob` to a separate injected bean so `@Transactional` applies.

### BE-2
**Unbounded page size on most list endpoints (DoS/OOM).** `- [ ]`

- **What.** `PageRequest.of(page, size)` built from a raw `size` param with no upper bound: `LeadController.kt:46-47,65-66`, `QuoteController.kt:38,56`, `ProductController.kt:34,43,142`, `WarehouseController.kt:34,43,165`, `AuthController.kt:225`. `?size=100000000` ⇒ giant query + full DTO serialization (worsened by OSIV holding the connection through serialization). `EmailController.list` uses `@PageableDefault` (capped) — inconsistent.
- **Where.** Controllers above.
- **Fix.** Clamp (`size.coerceIn(1,200)`) or `@Validated`+`@Max`, or standardize on the `Pageable` resolver like `EmailController`.

### OPS-1
**Production exposure: public Swagger + credentialed wildcard-capable CORS + no security headers/HSTS.** `- [ ]`

- **What.** (a) `SecurityConfig.kt:68-73` `permitAll()` for `/swagger-ui/**` and `/v3/api-docs/**` (springdoc is an unconditional dependency) ⇒ full unauthenticated API disclosure in prod. (b) CORS sets `allowCredentials=true` with `allowedOriginPatterns` (which permit `*`-style patterns) and `allowedHeaders=["*"]`; only guard is `SecretsGuard`, skipped on the empty-profile path; default origin falls back to `http://localhost:3000` (`SecurityConfig.kt:26,31-38`). (c) The filter chain never calls `.headers{}` ⇒ no HSTS, `X-Content-Type-Options`, or frame options (`SecurityConfig.kt:42-90`).
- **Where.** `SecurityConfig.kt:26,31-38,68-73`; `OpenApiConfig.kt`.
- **Fix.** Disable springdoc in prod (`springdoc.*.enabled=false`) or require auth; reject `*` in the CORS bean and constrain `allowedHeaders`; drop the localhost default; add `.headers{ httpStrictTransportSecurity{…}; contentTypeOptions{}; frameOptions{} }`.

### OPS-2
**No production datasource profile / no fail-fast on DB config.** `- [ ]`

- **What.** No `application-prod.yaml` and no `spring.datasource.*`; the app relies on undocumented `SPRING_DATASOURCE_*` env vars not present in `fly.toml [env]` or `.env.example`. No fail-fast like there is for `JWT_SECRET`/mail.
- **Why dangerous.** Opaque startup failure if env vars are missing; and (critically) **which role connects** determines MT-1 — this is where the NOBYPASSRLS/`SET ROLE` wiring must live.
- **Where.** `application.yaml` (no datasource), `application-local.yaml:5-6`, `fly.toml`.
- **Fix.** Add a prod profile binding datasource (+ the `SET ROLE` from MT-1) to no-default env vars; document in `.env.example`; assert presence at startup.

### BE-3
**Whole-CSV import runs in a single `@Async @Transactional`.** `- [ ]`

- **What.** `CsvImportProcessor.process` (`CsvImportProcessor.kt:29-69`) is `@Transactional` and loops every row calling `crmApi.create*`/`inventoryApi.create*` inside one transaction; a fatal mid-file error `throw e` (`:68`) rolls back all already-imported rows.
- **Why dangerous.** A large file holds a DB connection for the whole import (pool starvation under OSIV) and silently discards committed-looking progress on failure.
- **Where.** `CsvImportProcessor.kt:29-69`.
- **Fix.** Remove method-level `@Transactional`; commit per row/chunk via `TransactionTemplate`/`REQUIRES_NEW`.

---

## 🟡 Medium

### SEC-5
**Password-reset row keyed by email globally → griefing DoS + no user/tenant binding.** `- [ ]`
- `PasswordReset` PK is `email` (`PasswordReset.kt:11-13`, `V001:139-140`); upserted by email (`AuthService.kt:250-255`), validated on email+token+expiry only (`:274-294`). Any party calling `/auth/forgot-password` for a victim overwrites the victim's pending token (combined with the per-(email,IP) limiter, can keep a victim from completing a reset). No `userId`/`tenantId` anchor — becomes a cross-tenant takeover primitive if email-uniqueness ever goes per-tenant.
- **Fix.** Key reset rows by a random surrogate id / `userId`; allow multiple outstanding tokens; verify by hashing the supplied token then checking the bound user; add `userId`/`tenantId` columns.

### SEC-6
**`forgot-password` timing/latency enumeration oracle.** `- [ ]`
- `AuthService.kt:240-242` returns immediately for an unknown email, **before** the ~300ms BCrypt encode (`:248`) and SMTP send (`:258`). Real emails are measurably slower ⇒ enumeration. The login path equalizes timing with a dummy hash; forgot-password does not.
- **Fix.** Perform a dummy `passwordEncoder.encode(...)` (or fixed delay) on the unknown-email branch; keep responses identical.

### SEC-7
**Dummy bcrypt cost 10 vs real cost 12 reopens the login timing oracle.** `- [ ]`
- `DUMMY_BCRYPT_HASH = "$2a$10$…"` (`AuthService.kt:361`) but the real encoder is `BCryptPasswordEncoder(12)` (`PasswordConfig.kt:16`) ⇒ unknown-email check ~4× faster than a real wrong-password check.
- **Fix.** Regenerate the dummy at cost 12 (ideally derive from the configured encoder at startup).

### SEC-8
**JWT lacks `iss`/`aud`; token kinds separated only by a self-asserted `type`; no secret-entropy enforcement.** `- [ ]`
- `parseClaims` verifies signature+expiry only (`JwtTokenProvider.kt:147-153`); access/refresh/mfa tokens are all signed by the same key and distinguished by `"type"` (`:96-100`). No confusion bug today (checks are in the right places) but no defense in depth. `Keys.hmacShaKeyFor` (`:30`) throws only if <32 bytes — no entropy validation; HS256 shared key means any secret-holder can mint tokens for any tenant.
- **Fix.** Set and `require` `iss`+`aud` per token kind inside `parseClaims`; enforce documented secret entropy and fail closed; consider RS256/EdDSA.

### SEC-9
**Weak password-policy defaults.** `- [ ]`
- `PasswordPolicyService.kt:8-11`: min length 8; `require-uppercase/digit/special` default **false**. "password" passes unless overridden.
- **Fix.** Stronger defaults and/or breach-list check.

### SEC-10
**In-memory rate limiters (multi-node ineffective) + `X-Forwarded-For` lockout dodge.** `- [ ]`
- `LoginAttemptTracker`/`ForgotPasswordAttemptTracker`/`RegistrationAttemptTracker` use a local Caffeine cache (per-instance, wiped on deploy) — brute-force protection degrades to ~N× threshold across N pods. `RateLimiter` interface exists for a Redis swap but no distributed impl. Also `clientIp` is `request.remoteAddr` (`AuthController.kt:38`) with `forward-headers-strategy: framework` (`application.yaml:53`) ⇒ if exposed without a trusted proxy stripping XFF, attacker rotates the IP part of the `(email,IP)` key.
- **Fix.** Back the limiters with Redis for multi-node; ensure only a trusted proxy sets XFF (or pin to proxy-validated client IP).

### MT-4
**`users.email` is globally unique, not per-tenant.** `- [ ]`
- `uq_users_email UNIQUE (email)` (`V001:162`) — unlike `roles`/`groups` (`UNIQUE (tenant_id, name)`). A given human (email) can belong to only one tenant system-wide (a real limitation for partner/parent-child tenants), and registration returns "email taken" across tenants (existence oracle). Upside: makes login-by-email unambiguous.
- **Fix.** Decide intentionally — if multi-tenant membership is needed, move to `(tenant_id, email)` and resolve tenant at login; else document and ensure registration errors don't leak cross-tenant existence.

### BE-4
**OSIV enabled and the tenant filter is coupled to it.** `- [ ]`
- `spring.jpa.open-in-view` left default `true`; `TenantFilterInterceptor.kt:18-23` *depends* on OSIV. OSIV holds the DB connection through view rendering/serialization (throughput risk), yet disabling it would break MVC-path tenant filtering.
- **Fix.** Keep OSIV for now but document the coupling and size the pool for it; longer-term make `HibernateTenantFilterAspect` the sole per-transaction enabler so OSIV can be turned off.

### BE-5
**Negative inventory quantity accepted.** `- [ ]`
- `SetInventoryRequest.quantity:Int` has no `@Min(0)` (`ProductDtos.kt:52-57`); written straight to `ProductInventory.onHand` (`ProductService.kt:140,149`). Peers (`ReserveRequest` etc.) use `@Min(1)`.
- **Fix.** Add `@field:Min(0)`.

### BE-6
**N+1 on list endpoints (batch-mitigated) + `getLowStock` unpaged.** `- [ ]`
- Paginated Lead/Quote/Product/Warehouse lists serialize lazy `tags`/`items` without a fetch join (`LeadService.kt:561` etc.); softened by `default_batch_fetch_size:30` (~1 query/30 rows). `InventoryMovementService.getLowStock` (`:51-74`) pulls **all** products `unpaged` then sums inventory per product in memory (true unbounded N+1).
- **Fix.** `@EntityGraph(["tags"])` on list queries; replace `getLowStock` with a `GROUP BY … HAVING SUM(on_hand) <= threshold` aggregate.

### OPS-3
**No connection-pool sizing, structured logging, or correlation IDs for prod.** `- [ ]`
- No Hikari sizing/`max-lifetime`/`connection-timeout` on the prod path (defaults vs. serverless Neon with `min_machines_running=0`); no `logback-spring.xml`, JSON logs, or trace/correlation IDs (Modulith observability on classpath but unconfigured). *(Done well: SQL logging is off — no PII/secret SQL leakage.)*
- **Fix.** Explicit pool config in prod profile; `logback-spring.xml` with JSON + MDC trace id.

### OPS-4
**Encryption converter can silently fall back to plaintext; dead JWT default-marker guard.** `- [ ]`
- `AesGcmEncryptionConverter.convertToDatabaseColumn` returns plaintext when `secretKey == null` (`:56`) — mitigated today because `EncryptionKeyInitializer` fails fast at boot, but the converter isn't independently fail-closed. `SecretsGuard.DEFAULT_JWT_SECRET_MARKER` (`:104`) matches a string that exists nowhere ⇒ that branch can never fire (false confidence).
- **Fix.** Make the converter throw (not passthrough) on null key outside dev; remove/correct the dead JWT marker check.

### FE-1
**Client-side route authorization is cosmetic only.** `- [ ]`
- `auth.global.ts` checks only `isAuthenticated`; pages carry no `permission` meta; nav links are filtered but typing `/settings/users` renders the shell for any authenticated user (`web/app/middleware/auth.global.ts`, `layouts/default.vue:16-23`). Backend `@PreAuthorize` is the real gate, so this is UX/defense-in-depth.
- **Fix.** Add a `permission` route-meta convention checked in the global middleware.

### FE-2
**SSR bakes authenticated data into the hydration payload.** `- [ ]`
- `useAsyncData` runs server-side with the cookie token, serializing tenant data into `__NUXT__` HTML (`usePaginatedList.ts:42-55`, others). Pinia is request-scoped (no cross-request bleed), but it widens exposure to any HTML cache/log and to no-permission pages (FE-1).
- **Fix.** Gate authenticated fetches with `{ server:false }` or disable SSR for the authenticated shell; ensure no intermediary caches authenticated HTML.

### FE-3
**`useApi` no retry after refresh + stale cross-user state after logout.** `- [ ]`
- On 401, `useApi` refreshes but never re-issues the original request (`useApi.ts:21-29`) ⇒ every ~15 min each call fails once. `logout()` clears only cookies+user; `useState`/`useAsyncData` caches (users, pipelines, tenants, dashboard, inventory) persist on SPA logout ⇒ User A's data can render for User B on the same tab (`auth.ts:96-101`, `useDomainLookups.ts:38-47`, `useInventoryLookups.ts`).
- **Fix.** Re-run the request after a successful refresh (cap one retry); call `clearNuxtState()`/`clearNuxtData()` (or hard-reload) on logout.

### INJ-1
**CSV formula injection on all exports.** `- [ ]`
- `CsvExportService.kt:24-53` writes user fields (name, title, description, sku, website…) verbatim. Commons CSV quotes structure but does **not** neutralize leading `= + - @ \t \r` ⇒ `=HYPERLINK(...)`/`=cmd|'/c calc'!A1` executes in spreadsheet apps. Public web-form submissions feed these fields, so an external attacker can plant payloads. Served raw via `/persons/export` etc. (`ImportController.kt:144-160`).
- **Fix.** Prefix any cell whose first char ∈ `= + - @ \t \r` with `'` in a shared helper used by all exporters.

### INJ-2
**Hand-rolled CSV builder with no escaping.** `- [ ]`
- `AttributeService.downloadCsv` (`:263-271`) string-concatenates `attr.code`/`adminName` (user-set) with no quoting at all ⇒ comma/quote/newline forges columns, plus the same formula injection as INJ-1. Served by `AttributeController.kt:184`.
- **Fix.** Use `CSVPrinter` (like `CsvExportService.writeCsv`) + the formula-prefix guard.

### INJ-3
**File upload validated by client-declared Content-Type only.** `- [ ]`
- `FileUploadGuard.validate` (`:58-79`) trusts `file.contentType` (`:72`) — no magic-byte/extension check; `ALLOWED_DOCUMENT_TYPES` includes `image/svg+xml` (`:116`, script-capable). Mitigated: downloads use `ContentDisposition.attachment()` (`EmailController.kt:231`, `ActivityController.kt:252`), so risk reappears only if anything serves these inline/preview.
- **Fix.** Detect real type via the already-present Apache Tika; enforce an extension allow-list; keep `attachment`; drop `image/svg+xml` unless sanitized.

### API-1
**`ConstraintViolationException` mapped to 422 instead of 400.** `- [ ]`
- `GlobalExceptionHandler.kt:41-50` maps method/param constraint violations (bad query/path params) to 422; convention is 400. Body validation at 422 is fine; this interacts with the BE-2 fix (added param constraints would surface as 422).
- **Fix.** Map `ConstraintViolationException` → `HttpStatus.BAD_REQUEST`.

---

## 🔵 Minor

### INJ-4
**`OutboundUrlValidator` misses `0.0.0.0`/`::` and IPv4-mapped IPv6.** `- [ ]`
`checkAddress` (`OutboundUrlValidator.kt:94-118`) lacks `addr.isAnyLocalAddress()`, and the metadata byte-check (`:110-113`) only runs for `raw.size==4`, so `[::ffff:169.254.169.254]` skips it. Tenant webhook URLs flow through here. *(Redirects are NOT followed — JDK `HttpClient` default `Redirect.NEVER` — so the redirect-bypass is not exploitable.)* **Fix.** Add `isAnyLocalAddress` check; normalize IPv4-mapped IPv6 and run v4 rules on the embedded address.

### INJ-5
**Captcha verifier concatenates unescaped user token into HTTP body.** `- [ ]`
`WebFormCaptchaVerifier.kt:33` splices `request.captchaToken` (unauthenticated public submit) unescaped into `secret=…&response=$token&remoteip=…` ⇒ HTTP parameter injection into Google `siteverify` (not SSRF; fixed URL). **Fix.** `URLEncoder.encode(token, UTF_8)` or send a `MultiValueMap` form body.

### INJ-6
**Email-template `preview` returns interpolated-but-unsanitized HTML.** `- [ ]`
`EmailTemplateService.kt:80-81` interpolates raw `context` into the (save-time-sanitized) HTML and returns it without re-sanitizing. *Send* paths are safe (`MailSenderService.kt:43`, `MarketingService.kt:142-143`). Reflected XSS only if the FE renders preview as raw HTML. **Fix.** Sanitize the interpolated preview before returning, or treat preview as untrusted on the FE.

### BE-7
**`getStock` returns bare 400 bypassing ProblemDetail.** `- [ ]`
`InventoryMovementController.kt:24-27` returns `ResponseEntity.badRequest().build()` (empty body) when `warehouseId` is null; `warehouseId` is `required=false` but actually mandatory. **Fix.** Make it a required `@RequestParam` and delete the manual null check.

### BE-8
**Other `massDestroy` use per-id find+save loops.** `- [ ]`
`QuoteService.kt:218-225`, `ProductService.kt:110-118`, `WarehouseService.kt:163-170`, `ActivityService.kt:211-233`, `EmailService.kt:272-302` — O(N) round-trips (bounded at 500). Inconsistent with Lead's bulk path; note these are *safer* re: MT-2 (entity-by-entity loads are tenant-filtered) but slower. **Fix.** Optional: convert to bulk modifying queries *with* a tenant predicate (see MT-2).

### DATA-1
**`EmailRepository.reassignPerson` bulk UPDATE has no tenant predicate.** `- [ ]`
`EmailRepository.kt:56` `UPDATE Email e SET e.personId=… WHERE e.personId=:source` — internal merge call (source person validated upstream), but the statement itself is unscoped (same class as MT-2, lower reachability). **Fix.** Add `AND e.tenantId = :tenantId`.

### DATA-2
**Share-policy filter treats a missing field as `""` → over-shares owner's own records.** `- [ ]`
`TenantSharePolicyFilterEvaluator` `compare` (`:106-125`) resolves an absent field to `""`, so `neq`/`not_in`/`contains` on a missing field can match, and `{}` matches all. This over-shares the **owner's own** records to the **intended** consumer only — not a cross-tenant leak (candidate set is hard-scoped to owner tenant in `ShareMaterializationWorker.kt:167-172`). **Fix.** Treat missing field as non-match for negative ops; reject empty filters or define them explicitly.

### SEC-11
**API-key hash compare not constant-time; no server-side pepper.** `- [ ]`
`ApiKeyService.authenticateByKey` (`:83`) does a DB equality match on unsalted SHA-256 `key_hash`. Keys are 256-bit random, so impractical to exploit; documenting only. **Fix (optional).** Add a server-side pepper before hashing.

### SEC-12
**`login`/`completeMfaLogin` not `@Transactional`.** `- [ ]`
`AuthService.kt:71,124` perform `loginHistoryRepository.save` + refresh-session save in separate transactions; a failure between leaves inconsistent rows. (`register`/`refresh`/`forgotPassword` are correctly `@Transactional`.) **Fix.** Annotate both.

### OPS-5
**Container/CI hardening.** `- [ ]`
`api/Dockerfile:17` floating `eclipse-temurin:25-jre` (unpinned), no `USER` (runs as root), no `HEALTHCHECK`, `dependencies || true` (`:10`) swallows failures. `application.yaml:19` 200MB multipart on a 1GB VM. `deploy-api.yml:19` `workflow_dispatch` bypasses the CI gate. **Fix.** Non-root `USER`, pin base by digest, add healthcheck; lower multipart cap; gate manual deploys.

### CQ-1
**Documentation drift.** `- [ ]`
Comments reference non-existent artifacts: `RLS (V040)` (latest is V026), `sharedResourceFinder` (`Lead.kt:28`), "Sprint 2b" (sharing), and `V020`'s claim that audit writes "use BYPASSRLS" (no role config supports it). **Fix.** Align comments with reality (ties to MT-3).

### FE-4
**Misc frontend.** `- [ ]`
`layouts/default.vue:220` hardcodes `github.com/benjamincanac.png` as avatar fallback (external request + wrong identity). `reset-password.vue:28` `setTimeout(router.push)` without unmount cleanup. `fetchMe`/`useDownload` bypass `useApi` (no refresh-on-401). **Fix.** Local placeholder avatar; clear the timeout on unmount; route downloads through `useApi`.

### BE-9
**`WorkflowEngine` workflow-level failure silently logged.** `- [ ]`
`WorkflowEngine.kt:69-72` catches per-workflow errors and only logs at `warn` (per-action failures *are* persisted at `:95-100`, which is good). **Fix.** Persist a workflow-level failure record for observability.

### FE-5
**Leads page issued `GET /api/pipelines/{id}/stages` → 405, leaving the mass-move stage list empty.** `[x]` (fixed 2026-06-09)
- **What.** `web/app/pages/leads/index.vue` fetched stages from `GET /api/pipelines/{id}/stages`, but that path is **POST-only** on the backend (`PipelineController`), so it returned 405. The call was wrapped in `.catch(() => [])`, so the page rendered but the mass-action "move to stage" dropdown was silently empty and the browser console logged a 405 on every Leads visit. Not in the original audit because per-page Nuxt components were sampled, not read in full; surfaced by the end-to-end Playwright walk-through.
- **Fix (applied).** Derive the stage list from the already-loaded `GET /api/pipelines` response (each `PipelineResponse` carries its `stages` inline) instead of a second request. Removes the 405 and an unnecessary round-trip. ESLint clean.

### BE-10
**Soft-deleted product SKUs are never released; recreating a SKU throws 500.** `- [ ]`
- **What.** Products soft-delete (`deleted_at`), but `uq_products_tenant_sku` is a plain `UNIQUE (tenant_id, sku)` index — **not** partial (`WHERE deleted_at IS NULL`). So a soft-deleted product keeps its SKU reserved forever, and `POST /products` with that SKU hits a unique-constraint violation that surfaces as a generic **500** (not a 409/422). Found while re-seeding demo data (delete-then-recreate the catalog).
- **Why it matters.** A normal user workflow — delete a product, later recreate it (or a new product) with the same SKU — fails opaquely. Also blocks any idempotent re-seed/import that reuses SKUs.
- **Where.** `uq_products_tenant_sku` (products migration); `ProductService.create`; `GlobalExceptionHandler` (unique violation → 500).
- **Fix.** Make the index partial: `... UNIQUE (tenant_id, sku) WHERE deleted_at IS NULL`; and map `DataIntegrityViolationException` to 409 with a clear message. (Same applies to any other soft-deleted entity with a non-partial unique index.)

### BE-11
**Stock totals / low-stock sum inventory across soft-deleted warehouses.** `- [ ]`
- **What.** `InventoryMovementService.getLowStock` (and stock aggregation generally) sums `product_inventories` rows by `product_id` with **no filter on whether the owning warehouse is soft-deleted** (`InventoryMovementService.kt:59-62`). When a warehouse is deleted, its inventory rows remain and keep counting toward the product's on-hand total, so totals over-count and the reorder list silently mis-reports. Found when a re-seed that recreated warehouses caused on-hand totals to double.
- **Where.** `InventoryMovementService.kt:51-74`; `product_inventories` ↔ `warehouses` relationship.
- **Fix.** Exclude inventory tied to soft-deleted warehouses (join + `warehouse.deleted_at IS NULL`), and/or cascade-soft-delete `product_inventories` when a warehouse is deleted. Ties to BE-6's suggestion to replace the in-memory sum with a `GROUP BY … HAVING SUM(...)` aggregate that can carry the predicate.

### FE-6
**`USelect` items with an empty-string `value` crash the whole page under NuxtUI 4 / Reka UI.** `[x]` (fixed 2026-06-09)
- **What.** Reka UI's `SelectItem` forbids `value=""` (empty string is reserved to clear the selection). Three pages defined an "All"/"None" option as `{ value: '' }`, which threw on render and tripped the page-level error boundary ("Something went wrong on this page — A `<SelectItem />` must have a value prop that is not an empty string"): `inventory/stock.vue` ("All locations"), `sharing/shared-with-me.vue` ("All types"), and `settings/marketing/index.vue` ("None", ×2 — latent, fires when the campaign modal opens). Surfaced by visual review of the screenshot tour (the error boundary renders without a console error, so the network/console smoke check didn't flag it).
- **Fix (applied).** Use `value: undefined` for the placeholder/clear option, matching the existing working convention in `activities/index.vue` and `quotes/index.vue`. Refs typed `string | undefined`.

### FE-7
**Users settings page used `usePaginatedList` against an array endpoint, so it always showed "0 users".** `[x]` (fixed 2026-06-09)
- **What.** `GET /api/users` returns a bare `List<UserResponse>` (the assignee-lookup consumers in `useDomainLookups.ts` and `activities/index.vue` rely on the full array). But `settings/users/index.vue` consumed it via `usePaginatedList`, which reads `.content`/`.totalElements` — undefined on an array — so the page rendered "0 total / No users found" even with users present (including the admin). This is the only `usePaginatedList` page pointed at a non-paginated endpoint (the others — leads/quotes/products/warehouses/persons/orgs/activities — are correctly paginated).
- **Fix (applied).** Fetch the array directly with `useAsyncData<UserResponse[]>` and filter client-side, keeping the `users/total/search/page/pending/refresh` bindings the template already uses. (Leaving `GET /users` as an array is correct — paginating it would break the lookup dropdowns that need every user.)

### FE-8
**Cross-tenant tenant names render as raw UUIDs on the Sharing pages.** `[x]` (fixed 2026-06-09)
- **What.** `useTenantNames` loads the tenant directory via `useAsyncData('sharing-tenants', () => can('tenants.view') ? api('/api/tenants') : [])`. The auth plugin is **client-only** (`web/app/plugins/01.auth.client.ts`), so during SSR there is no authenticated principal → `can('tenants.view')` is `false` → the directory resolves to `[]`, and `useAsyncData` caches that empty result (it doesn't refetch on the client). Result: `tenantName(id)` always hit its `${id.slice(0,8)}…` fallback, so Relationships and Shared-with-me showed UUIDs instead of the partner tenant's name. (Same root cause family as FE-2: SSR runs without the client-only auth.)
- **Fix (applied).** Mark that `useAsyncData` `server: false` so the directory is fetched on the client, after the auth plugin has populated the store. Names now resolve.
- **Related observation (not fixed).** `GET /api/tenants` returns the **entire** tenant directory (id/name/slug/status) to any tenant with `tenants.view`, which the relationship "target tenant" picker depends on — but it also lets any tenant enumerate every other tenant. Consider scoping the picker to an explicit invite/search flow if cross-tenant existence disclosure matters (cf. MT-4's existence-oracle theme).

---

## Coverage Map

**Read in full / personally verified (special-focus core):**
`shared/TenantContext`, `HibernateTenantFilterAspect`, `RlsTenantGucAspect`, `TenantFilterInterceptor`, `BaseEntity`, `SystemConfig`; `auth/config/JwtAuthFilter`, `auth/config/SecurityConfig`; migrations `V007`, `V011` (+ greps over all migrations for FORCE/role); `crm/lead` `Lead`/`LeadService`/`LeadController`/`LeadRepository`; sharing `ResourceVisibilityService`, `CrossTenantWriteInterceptor`, `RecordShareService`, `TenantRelationshipService`, `RelationshipController`; `WebFormSubmissionService`; `RlsTwoTenantIsolationTest`, `CrossTenantVisibilityIntegrationTest`, `init-non-superuser.sql`. **Codebase-wide structural sweeps:** `@Filter` coverage on every `@Entity` (all 38 tenant entities covered; only intentional cross-tenant/identity/auth tables lack it); every `@Modifying` bulk query (MT-2 / DATA-1 found).

**Covered by focused sub-audits (representative files read in full, not 100% of the module):**
- **Auth** (`auth/**`, JWT/refresh/API-key/MFA/password-reset/rate-limiters) — thorough.
- **Injection/SSRF/dynamic-query** (datagrid + share-policy filterJson, `OutboundUrlValidator`, webhooks, EAV, CSV/HTML/template/PDF, file upload, all `LIKE`/native queries) — thorough.
- **Backend patterns** (`crm/`, `inventory/`, `settings/`, `identity/`, `dashboard/`, `shared/web`) — transactions, exceptions, DTO leakage, N+1, validation, REST codes — representative sample.
- **Frontend** (`web/app` auth/state/SSR/API integration) — representative sample.
- **DevOps** (all config: yaml, Dockerfile, fly.toml, compose, CI/CD, build.gradle, SecretsGuard) — thorough.

**NOT exhaustively read (lower-risk; verify opportunistically):**
- Per-page Nuxt components and the Zod validation layer (only auth/state/data-fetch paths were read).
- `crm/activity`, `crm/email` inbound-parse internals, `crm/quote` PDF/totals math, `crm/datagrid` beyond filter safety, `crm/dashboard` service internals — sampled, not line-by-line.
- `settings/automation` `WorkflowConditionEvaluator`/`WorkflowEngine` action set, `settings/marketing` beyond the send worker — sampled.
- `inventory/transfer`, `inventory/reorder` workers — sampled.
- Migrations `V002–V006`, `V008–V010`, `V012–V026` — read selectively (seed/idempotency/index migrations not line-by-line).
- The full test suite (≈90 test files) was used as *evidence* of intent, not audited for correctness itself.

---

## Verified Safe / Done Well (do not re-flag)

- **Same-tenant read/write isolation** via Hibernate `@Filter` is solid: applied by interceptor (MVC) + aspect (async/`@Transactional`); `BaseEntity.@PrePersist` hard-fails writes without context; `tenant_id` is `updatable=false`. All 38 tenant entities carry the filter.
- **Sharing control plane** is correctly authorized: `sourceTenantId`/`actingTenantId` come from `principal.tenantId` (never the request body); `share()` verifies ownership via module ports; `reshare()` enforces MANAGE and caps the granted level; `revoke` checks participant; non-participants get 404 (no existence leak). `RelationshipController` derives tenant from the principal throughout.
- **Refresh-token rotation + reuse detection**: tokens stored as SHA-256 hashes; rotation revokes the old session; replay of a revoked token triggers **family-wide revocation**; `@Transactional(noRollbackFor=…)` ensures the revocation commits. (`AuthService.refresh`.)
- **All auth management/list/revoke endpoints scope by `@AuthenticationPrincipal`**, not the body (this is what keeps the no-`@Filter` auth tables safe today).
- **Native SQL queries** all use `@Param` binds **and** carry explicit `tenant_id = :tenantId` (the documented third layer) — e.g. dashboard queries. `UserRepository.reactivateByIds` is correctly tenant-scoped.
- **Dynamic filters are safe**: datagrid `applied` JSON is stored-only (never reaches a query) with closed allowlists; share-policy `filterJson` is evaluated in-memory against a `row_to_json` snapshot with a closed operator set and owner-tenant-scoped candidates; share-materialization table name comes from a closed `ResourceType` enum.
- **Webhooks do not follow redirects** (JDK `HttpClient` default `Redirect.NEVER`); `OutboundUrlValidator` blocks non-https, userinfo, loopback, RFC-1918, link-local, the metadata IP, and IPv6 ULA (residual gaps in INJ-4 only).
- **No DTO/entity leakage**: controllers return `*Response` DTOs; `passwordHash`/`Webhook.secret`/`SystemConfig` secret values are never exposed; no mass-assignment.
- **No `@Cacheable` anywhere** ⇒ no cross-tenant cache-poisoning surface.
- **Optimistic-lock / constraint exceptions** are mapped to 409; the catch-all logs server-side and returns a generic 500 (no stack-trace leakage). Creates return 201, deletes 204.
- **Secrets**: `.env` gitignored + dockerignored; fail-fast on missing `JWT_SECRET`/mail/CORS via no-default `${VAR}`; AES-256-**GCM** at rest; HMAC-verified inbound mail; SQL/param logging off. Actuator: only `/actuator/health` is web-exposed (no env/heapdump). `ddl-auto: validate` + Flyway enabled.
- **`HtmlSanitizer`** (Jsoup relaxed safelist, protocol-restricted) and **`TemplateInterpolator`** (fixed-regex substitution, no SSTI) are sound; **file storage** confines paths (`startsWith(rootPath)`, basename strip, regex sanitize) — no traversal.

---

## Suggested Fix Order for the Next Session

1. **Isolation first (the only true cross-tenant exposure):** MT-1 → MT-2. Add the boot-time 2-tenant self-check so CI guards it forever. Then MT-3 (stop the code from claiming sharing works).
2. **Auth/MFA:** SEC-1, SEC-2 (online MFA-bypass surface), then SEC-3/SEC-4 (web token custody + MFA login), then SEC-5/SEC-6/SEC-7.
3. **Exposure & delivery correctness:** OPS-1, OPS-2; BE-1, BE-2, BE-3.
4. **Everything else** by severity, using the Priority Index.

## Overall Assessment

- **Production-readiness: 5/10.** Excellent engineering and test discipline (would be ~8 for single-tenant correctness), held back because provable cross-tenant isolation *in the production config* is not established (MT-1/MT-2) and the flagship sharing feature is half-delivered + mis-documented (MT-3), plus real auth/MFA and delivery-correctness defects.
- **Confidence:** High in same-tenant correctness and the *design* of isolation/authorization; **low-to-moderate** in *as-deployed* multi-tenant isolation until MT-1/MT-2 are fixed and verified against the real DB role.
