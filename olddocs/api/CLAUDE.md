# CLAUDE.md — Synoptic Engine Backend (`api/`)

## Stack

- **Kotlin** + **Spring Boot 4** (WebMVC, not WebFlux)
- **Spring Data JPA** / **Hibernate 7** — `ddl-auto: validate`; schema owned by Flyway
- **PostgreSQL** with Row-Level Security (RLS) — 38+ tables
- **jjwt 0.13** for JWT, **Jackson 3** (`tools.jackson.databind`) for JSON
- **springdoc-openapi 3** — Swagger UI at `/api/swagger-ui/**`, docs at `/api/v3/api-docs/**`
  (all endpoints sit under `server.servlet.context-path=/api`; controllers use clean relative
  `@RequestMapping` paths — there is **no** `api.base-path` placeholder)
- **Caffeine** for in-process caches/rate limiters
- **Testcontainers** for integration tests (needs Docker)

## Commands

```bash
cd api
./gradlew testClasses          # fast compile check (no Docker)
./gradlew unitTests            # pure unit tests (no Spring/Testcontainers)
./gradlew test                 # full suite (needs Docker for Testcontainers + NOBYPASSRLS role)
./gradlew bootRun              # start server on :8090
```

New Flyway migrations: next file is `V027__…` (highest applied is `V026__mfa.sql`) — always
pair a new migration with entity changes in the **same commit** (`ddl-auto=validate` will fail
otherwise).

## Architecture rules

These rules drive code review and architecture decisions. Deviations require an explicit
decision note (see remediation plan `analysis/01-remediation-plan.md`).

### Rule 1 — Money is always `BigDecimal`

Use `BigDecimal` with explicit scale + `RoundingMode`. Never `Double` or `Float` for
monetary amounts. Canonical pattern: `divide(BigDecimal(100), 10, RoundingMode.HALF_UP)`.

### Rule 2 — Tenant isolation is two-layer

Primary isolation: **RLS policies** (`V007__sharing_and_rls.sql`) enforce tenant_id via
`app_current_tenant()`. Secondary: **Hibernate `@Filter`** (`tenantFilter`) applied by
`HibernateTenantFilterAspect` on every `@Transactional` method.

- `TenantContext` is set by `JwtAuthFilter` for HTTP requests and propagated to async
  threads by `TenantPropagatingTaskDecorator`.
- Null tenant (bootstrap, public endpoints, login) bypasses RLS by design — the policies
  start with `app_current_tenant() IS NULL OR …`.
- Native `@Query` that bypass the Hibernate filter **must** include an explicit
  `AND tenant_id = :tenantId` predicate (mirror `ActivityRepository.countCreatedInRangeNative`).

### Rule 3 — Sharing tables are intentionally cross-tenant

`record_shares`, `resource_visibility`, `tenant_*`, `cross_tenant_audit`,
`share_materialization_queue` do **not** extend `BaseEntity` and carry **no**
`@Filter`/RLS — they are cross-tenant by design. Authorization is service-layer only.

### Rule 4 — All entities extend `BaseEntity`

`BaseEntity` provides `id` (UUID, `@GeneratedValue`), `tenantId` (populated by
`@PrePersist` from `TenantContext`), `createdAt`, `updatedAt`, `version` (`@Version`).
Entities that also need `createdBy`/`updatedBy` extend `AuditableEntity` (which extends
`BaseEntity`). Neither `BaseEntity` nor `AuditableEntity` are `@Entity` themselves;
they are `@MappedSuperclass`.

### Rule 5 — Soft-delete everywhere (except sharing tables and audit)

Use `deletedAt: Instant?` for soft-delete. Active-entity queries use
`WHERE deleted_at IS NULL`. Hard-delete is only used for audit/retention workers and by
explicit decision (documented per-entity).

### Rule 6 — Repository queries: use `@Query`; do NOT introduce `JpaSpecificationExecutor` or `*Specs`

The codebase uses hand-written `@Query` (JPQL and native SQL) uniformly and it works.
**Do not** introduce `JpaSpecificationExecutor` / `*Specifications` classes.
Hand-written queries are clearer, easier to audit, and stay in lockstep with the DB schema.

### Rule 7 — Permission system

Permissions are owned by per-module `*PermissionRegistry` beans (e.g., `CrmPermissionRegistry`,
`SharingPermissionRegistry`). `BootstrapService` seeds them at startup (idempotent).
The seed migration (V008) pre-created some keys as a baseline; registries are the
**source of truth** going forward. ADMIN role maps to `RoleType.ALL`, expanded to all
registered keys in `UserService.toCredentials`.

Permission keys follow the pattern `module.action` (e.g., `leads.view`, `leads.edit`,
`tags.delete`, `share-policies.manage`). Parent keys (e.g., `leads`) act as grouping
labels in the UI but are not themselves used in `@PreAuthorize`.

### Rule 8 — Controller conventions

- Every `@RequestBody` that is bound to an application DTO **must** carry `@Valid`.
- Every endpoint **must** carry `@PreAuthorize("hasAuthority('…')")` unless it is
  explicitly a public endpoint (annotated with a comment explaining why).
- Request/response DTOs are in the same `web/` package as the controller and are
  named `*Request`/`*Response`.

### Rule 9 — Async / scheduled work

- `@Async` methods are dispatched via the `taskExecutor` bean in `AsyncConfig`, which
  uses `TenantPropagatingTaskDecorator` to carry `TenantContext` and `ActorContext`.
- Auditor fallback: `JpaAuditingConfig.auditorProvider` checks `SecurityContextHolder`
  first, then falls back to `ActorContext.get()` so workflow/import-created rows get
  `createdBy`/`updatedBy` on async threads.
- Scheduled workers are annotated `@Scheduled` and live in `*Worker` or `*RetentionWorker`
  classes. Configure intervals via `application.yaml` properties.

### Rule 10 — Status enums and DB column types

Status/type enums stored in the DB use either:
- Spring's default ordinal-based storage (avoid — fragile on reorder), OR
- A custom `AttributeConverter` for **canonical lowercase** string storage (preferred for
  enums whose DB values must be stable strings, e.g., `lead_status`, `email_status`).

Document the chosen strategy in the entity's Kdoc.

### Rule 11 — CORS and secrets discipline

- `CORS_ALLOWED_ORIGINS` defaults to `localhost:3000` only. Non-dev profiles must set
  explicit origins. Wildcard `*` with `allowCredentials=true` is rejected by `SecretsGuard`.
- Webhook secrets and `system_configs.value` (where `is_secret=true`) are AES-GCM
  encrypted at rest via `AesGcmEncryptionConverter`. The key is loaded from
  `SYNOPTIC_ENCRYPTION_KEY` (base64 32-byte).
- `SecretsGuard` refuses to start in non-dev profiles without required secrets.
- `server.forward-headers-strategy=framework` is set; the app must sit behind a trusted
  proxy for `X-Forwarded-For` to be reliable.

### Rule 12 — Rate limiters behind an interface

Rate limiters (`LoginAttemptTracker`, `ForgotPasswordAttemptTracker`) implement the
`RateLimiter` interface backed by a bounded Caffeine cache. Any distributed/Redis
implementation can swap in without touching callers. Mark cross-node adaptation points
with `// MULTI-NODE:` comments.

## Guardrails — intentional designs, do NOT "fix"

- RLS policies begin `app_current_tenant() IS NULL OR …` — null-tenant transactions
  (bootstrap, login, public web-form/inbound-mail) bypass RLS **by design**.
- Sharing-module tables intentionally have **no** `@Filter`/RLS.
- `TenantContext` may be null on public endpoints; `BaseEntity.@PrePersist` is the
  write-side gate.
- Permissions catalog is owned by `*PermissionRegistry` beans; V008 seed rows are
  legacy baseline only — do not add new permission rows to migrations.
- `OPEN_IN_VIEW` is currently relied upon (no `spring.jpa.open-in-view=false`); this
  coupling is documented and intentional for the current single-node topology.

## Package layout

```
api/src/main/kotlin/com/synopticengine/api/
├── auth/                     # JWT, BCrypt, login/logout/refresh/reset
├── bootstrap/                # BootstrapService (seeds permissions + seed tenant)
├── crm/
│   ├── activity/             # Activities, participants, file uploads
│   ├── automation/           # CRM workflow adapter
│   ├── contact/              # Persons, Organizations
│   ├── dashboard/            # Stats aggregates
│   ├── datagrid/             # Saved column filters (user-scoped)
│   ├── email/                # Email CRUD, drafts, send, forward, inbound-parse
│   ├── lead/                 # Leads, Pipelines, Stages, LeadSource, LeadType
│   ├── quote/                # Quotes, QuoteItems, PDF generation
│   ├── scoping/              # ScopeResolver (view scoping by user/team)
│   └── tag/                  # Cross-resource tags
├── dashboard/                # Top-level OOM-safe dashboard (uses crm/dashboard stats)
├── identity/                 # Users, Roles, Groups, Permissions, Tenants
├── inventory/                # Products, Warehouses, WarehouseLocations, ProductInventory
├── settings/
│   ├── attribute/            # Custom attributes (EAV pattern)
│   ├── automation/           # Workflows, Webhook dispatcher, WorkflowEngine
│   ├── dataimport/           # CSV import
│   ├── email/                # Email templates
│   ├── marketing/            # Marketing campaigns
│   ├── systemconfig/         # Per-tenant config catalog
│   └── webform/              # Web forms + CAPTCHA + submission → lead
├── shared/                   # Cross-cutting utilities
│   ├── config/               # JPA auditing, async, password, OpenAPI, RLS GUC
│   ├── crypto/               # AES-GCM encryption converter
│   ├── domain/               # BaseEntity, AuditableEntity
│   ├── email/                # MailSenderService (async SMTP)
│   ├── security/             # OutboundUrlValidator (SSRF guard)
│   └── upload/               # FileUploadGuard (MIME + size)
└── sharing/                  # Cross-tenant sharing: relationships, policies, visibility
```
