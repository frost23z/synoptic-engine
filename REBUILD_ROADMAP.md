# Manual Rebuild Roadmap — Synoptic Engine

Goal: rebuild the whole platform **by hand**, understanding every line, instead of
accepting AI-generated bulk. This is a learning + ownership exercise, so the order here is
**deliberately different** from how the code currently sits on disk.

## The one rule that changes everything

The existing repo is laid out **horizontally** (all entities, then all repos, then all
services…). Do **NOT** rebuild it that way — you'll write 300 files before anything runs.

Rebuild **vertically**: one feature working end-to-end (DB → entity → repo → service →
controller → page) before starting the next. You always have a running app, every phase is
demoable, and you learn how the layers connect instead of memorizing them in isolation.

Treat the existing code as a **reference answer key**, not a thing to copy. For each slice:
1. Read the existing files for that feature.
2. Close them. Write your own from the spec + your understanding.
3. Diff against the original. Understand every difference before moving on.

---

## Working rhythm (per vertical slice)

Backend slice order — always this sequence:
1. **Migration** (`V0xx__*.sql`) — tables, indexes, RLS policy, FK triggers.
2. **Entity** — extend `BaseEntity`/`AuditableEntity`; pair with the migration in one commit
   (`ddl-auto=validate` fails otherwise).
3. **Repository** — hand-written `@Query` only (no `JpaSpecificationExecutor`).
4. **Service** — business rules, authorization, transactions.
5. **Controller + DTOs** — `@Valid` on every body, `@PreAuthorize` on every endpoint.
6. **Tests** — Testcontainers integration test proving the slice + tenant isolation.

Frontend slice order:
1. `./gradlew dumpOpenApiSpec` → `pnpm openapi:gen` (regenerate types/Zod from the spec).
2. **Composable** wrapping `useApi()` (never raw `$fetch` in components).
3. **Page(s)** using the shared library (`AppListTable`, `AppPageHeader`, …).
4. **Playwright e2e** against the live stack.

Commit at the end of each slice. One slice ≈ one PR.

---

## Phase 0 — Foundations & tooling (no features yet)

Get a skeleton that compiles and runs on both stacks.

- [ ] Repo scaffolding, `.gitignore`, `.editorconfig`, lefthook pre-commit hooks.
- [ ] `api/`: Gradle Kotlin DSL, Spring Boot 4 (WebMVC), dependencies (JPA/Hibernate 7,
      Flyway, jjwt, Jackson 3, springdoc, Caffeine, Testcontainers).
- [ ] `api/compose.yaml`: Postgres with a `NOBYPASSRLS` app role (needed for RLS to bite).
- [ ] `application.yaml`: `ddl-auto: validate`, Flyway on, profiles (dev/test/prod).
- [ ] `web/`: Nuxt 4, @nuxt/ui v4, Pinia, VueUse, Tailwind v4, Tabler icons, strict TS.
- [ ] Wire `typecheck`/`lint`/`format`/`build` + `openapi-ts.config.ts`.
- [ ] **CI from day one** (`.github/workflows/ci.yml`): backend test, web lint/build, and the
      OpenAPI **drift gate** (re-dump spec, assert it equals committed `api-docs.json`). The
      current repo never built this — do it now so drift can't accumulate.

Exit: `./gradlew bootRun` starts on :8090, `pnpm dev` serves :3000, CI is green on an empty app.

---

## Phase 1 — Walking skeleton (prove the wiring, no auth)

- [ ] `V001` minimal: one throwaway table, just to prove Flyway runs.
- [ ] A `/health` or `/ping` controller returning JSON.
- [ ] One Nuxt page calling it through `useApi()` and rendering the result.
- [ ] First Testcontainers test that boots Postgres and hits the endpoint.

Exit: front page shows live data from the DB. The whole pipe works.

---

## Phase 2 — Identity & multi-tenancy spine ⭐ (the hardest, do it early)

Everything else depends on this. Build it slowly and test isolation hard.

- [ ] `shared/domain`: `BaseEntity` (UUID id, `tenantId` via `@PrePersist`, timestamps,
      `@Version`) and `AuditableEntity` (`createdBy`/`updatedBy`). Both `@MappedSuperclass`.
- [ ] `TenantContext` + propagation (`JwtAuthFilter` sets it; `TenantPropagatingTaskDecorator`
      carries it to async threads).
- [ ] **RLS**: a baseline migration creating `app_current_tenant()` GUC and the
      `app_current_tenant() IS NULL OR tenant_id = …` policy pattern. The null-tenant bypass
      (for bootstrap/login/public) is **intentional** — design it in, don't bolt it on.
- [ ] Hibernate `@Filter` (`tenantFilter`) + `HibernateTenantFilterAspect` as the secondary
      layer applied on every `@Transactional` method.
- [ ] `identity/`: Tenants, Users, Roles, Groups, Permissions (entities, repos, services,
      controllers). Permission keys as `module.action`.
- [ ] Per-module `*PermissionRegistry` beans + `BootstrapService` that seeds permissions and a
      seed tenant idempotently at startup. Registries are the source of truth (not migrations).
- [ ] `auth/`: BCrypt, JWT issue/verify (jjwt), `POST /auth/login` → access+refresh,
      `/auth/refresh`, logout, password reset.
- [ ] Frontend: `auth` middleware (all pages authed except login), auth store, login page,
      401-refresh handling inside `useApi()`.

Exit: log in, get a token, hit an authed endpoint, and a cross-tenant read returns **nothing**
(write an integration test that proves tenant A cannot see tenant B's rows via both RLS and
the Hibernate filter).

---

## Phase 3 — First real CRM vertical: **Leads** 🎯

This is the template you'll repeat. Take your time; nail the pattern.

- [ ] Backend: Leads, Pipelines, Stages, LeadSource, LeadType (migration → … → tests).
      Status enums via `AttributeConverter` for stable lowercase strings.
- [ ] Frontend: leads list (`AppListTable` + `usePaginatedList`), create, `[id]` detail,
      kanban-by-stage. Validate forms with the generated Zod schema.

Exit: full CRUD on leads through the UI, with pagination, search, permissions, and an e2e test.
You now own the end-to-end pattern.

---

## Phase 4 — CRM core (repeat the pattern)

One vertical slice each, in this order (dependencies first):

- [ ] **Contacts** — Persons & Organizations (referenced by leads/quotes/activities).
- [ ] **Tags** — cross-resource tagging (`AppTagManager`).
- [ ] **Activities** — activities, participants, file uploads (`FileUploadGuard` MIME+size).
- [ ] **Dashboard** — stats aggregates (mind the OOM-safe query patterns; explicit
      `AND tenant_id = :tenantId` on every native query).
- [ ] **DataGrid** — user-scoped saved column filters (`AppSavedFilters`).

---

## Phase 5 — Sales & comms

- [ ] **Quotes** — quotes + items + **PDF generation**. Money is **always `BigDecimal`**
      with explicit scale + `RoundingMode` — never `Double`.
- [ ] **Email** — CRUD, drafts, send (async SMTP via `MailSenderService`), forward,
      inbound-parse → activity. `mail/*` pages.

---

## Phase 6 — Inventory / ERP

- [ ] Products, Warehouses, WarehouseLocations, ProductInventory.
- [ ] Inventory movements, transfers, reorder/stock views.
- [ ] Frontend: products, warehouses, `inventory/*` pages.

---

## Phase 7 — Settings & automation (the long tail)

Each is its own slice:

- [ ] **Attributes** — custom attributes (EAV pattern). Read the existing one carefully; EAV is
      easy to get subtly wrong.
- [ ] **Workflows + WorkflowEngine** — and the **Webhook dispatcher** (`OutboundUrlValidator`
      SSRF guard; AES-GCM-encrypted secrets via `AesGcmEncryptionConverter`; delivery runs +
      retry worker).
- [ ] **Web forms** — form builder + CAPTCHA + public submission → lead (null-tenant public
      endpoint).
- [ ] **CSV import** — staged import with stats.
- [ ] **Email templates**, **marketing campaigns**, **per-tenant system config** catalog
      (secret values encrypted at rest).

---

## Phase 8 — The USP: cross-tenant resource sharing ⭐

The differentiator. Note: these tables are **intentionally cross-tenant** — they do **NOT**
extend `BaseEntity`, carry **no** `@Filter`/RLS, and authorization is **service-layer only**.
Don't "fix" that.

- [ ] `tenant_relationships`, `share_policies`, `record_shares`, `resource_visibility`,
      `cross_tenant_audit`, `share_materialization_queue` (+ cross-tenant FK triggers).
- [ ] RecordShare / Relationship / SharePolicy / CrossTenantAudit controllers + services.
- [ ] Frontend: `sharing/*` pages + `ShareRecordModal`, `sharing/shared-with-me`, audit view.

Exit: tenant A shares a record with tenant B; B sees it; the cross-tenant audit logs it; an
isolation test proves nothing leaks beyond the explicit share.

---

## Phase 9 — Enterprise hardening

- [ ] **Audit log** (V020) + retention worker.
- [ ] **Refresh sessions** (V015), **login history** (V024).
- [ ] **API keys** (V025) — hashed, scoped.
- [ ] **MFA** (V026) — TOTP enroll/verify.
- [ ] Rate limiters behind the `RateLimiter` interface (Caffeine-backed; mark `// MULTI-NODE:`
      swap points), `SecretsGuard`, CORS discipline.

---

## Phase 10 — Frontend library extraction & polish

Counter-intuitively, build pages slightly duplicated first, then **extract** the shared library
once you've felt the duplication (that's how the originals were derived — ~65–75% dup removed):

- [ ] `AppPageHeader`, `AppListTable`, `AppRowActions`, `AppMassActionBar`, `AppPagination`,
      `AppEmptyState`, `AppConfirmModal`, `AppDetailLayout`, charts/stat cards, timeline.
- [ ] Composables: `usePaginatedList`, `useDeleteResource`, `useMassSelect`, `useApi`,
      `usePermissions`, `useFormatters`, `useDownload`, `useTheme`, `useFormSubmit`.
- [ ] **Self-serve registration**: `/register` → public `POST /auth/register` → auto-login.
- [ ] Validation long-tail, theme picker, e2e sweep (`--workers=1` in the devcontainer).

---

## Sequencing summary

```
0 Foundations ─► 1 Skeleton ─► 2 Identity+Tenancy ─► 3 Leads (template)
   ─► 4 CRM core ─► 5 Sales/Comms ─► 6 Inventory ─► 7 Settings/Automation
   ─► 8 Cross-tenant Sharing (USP) ─► 9 Hardening ─► 10 FE library + polish
```

Phases 0–3 are the investment that makes 4–8 fast (each becomes "apply the Leads pattern").
Don't rush 2 — multi-tenant isolation bugs are the expensive ones.

## Guardrails — designs to preserve verbatim (don't "improve" them)

- RLS policies start with `app_current_tenant() IS NULL OR …`; null-tenant bypass is by design.
- Sharing tables: no `BaseEntity`, no RLS/filter — cross-tenant on purpose.
- Money = `BigDecimal` + explicit scale/`RoundingMode`, never `Double`/`Float`.
- Hand-written `@Query` only; no `JpaSpecificationExecutor`/`*Specs`.
- Permissions owned by `*PermissionRegistry` beans, not migration rows.
- Every native query that bypasses the Hibernate filter needs explicit `AND tenant_id = :tenantId`.
- New migration + matching entity change in the **same commit**.

## Reference material (keep open while building)

- `api/CLAUDE.md` — backend architecture rules (read before each backend slice).
- `web/CLAUDE.md` — frontend conventions + shared library API.
- `krayin-features-analysis/*.md` — per-module behavior, fields, business rules.
- `BACKEND_PLAN.md` / `FRONTEND_PLAN.md` — full feature inventory & status.
- `api-docs.json` — the API contract; the frontend is a pure function of it.
- The existing `api/src` and `web/app` — your answer key. Read, close, rewrite, diff.
```
