# Frontend Completion Plan — Synoptic Engine (`web/`)

> Authored: 2026-06-02
> Scope: complete the Nuxt 4 frontend to MVP — a workable UI over the (now
> complete) Krayin-parity + enterprise backend.
> Companion docs: `web/CLAUDE.md` (stack/conventions), `krayin-features-analysis/*`
> (the feature spec each page must implement), backend OpenAPI at
> `http://localhost:8090/v3/api-docs` + Swagger UI `/swagger-ui` (the API contract).
> Agentic/AI surfaces are explicitly OUT of MVP scope → see `FUTURE_AGENTIC_CRM.md`.

---

## Verdict: SALVAGEABLE — refactor onto a component library, do **not** rebuild

The foundation is sound: auth store (cookie tokens + 401-refresh), permission-gated
sidebar layout, theme/dark-mode, `useApi`/`usePermissions`, pnpm + ESLint + Prettier +
Lefthook + TS strict, Playwright harness. The API wiring is **correct** (`/api` prefix for
resources, bare `/auth` for auth). No `any`, no dead code.

**The problem is structural thinness, not bugs:** ~36 pages / ~11.6k LOC with **only one
shared component** (the theme-picker). List tables, pagination, search bars, delete
confirms, row-action menus, empty states, and form modals are copy-pasted **~12–22 times
each** — roughly **65–75% of page markup is duplicated**. Any change to a pattern today
means editing ~20 files.

**Strategy:** extract a small component + composable library, refactor existing pages onto
it (≈40% / ~4,500 LOC reduction), fix the handful of contract mismatches, then fill the
coverage gaps — with the cross-tenant **Sharing UI (the product USP, 0% built today)** as
the top net-new priority.

---

## Part 1 — Backend Contract Mismatches to Fix First (small, high-leverage)

1. **Generate types/client from OpenAPI.** Hand-written `app/types/*.ts` are incomplete
   (missing dashboard, workflows, webhooks, imports, attributes, web-forms, email-templates;
   no `QuoteResponse`/`MailResponse`). Add `openapi-typescript` against `/v3/api-docs` to
   generate a typed schema, and migrate types incrementally. **This permanently kills DTO
   drift** and is the single highest-leverage frontend task.
2. **Settle enum casing via the generated types.** The audit flagged `Lead.status` /
   `Quote.status` casing inconsistencies (lowercase comparisons vs uppercased filter sends).
   Note `api/CLAUDE.md` Rule 10: several backend enums (`lead_status`, `email_status`) use
   **canonical lowercase** string storage, while others (e.g. `Activity.type`) are uppercase.
   Don't guess — generate from OpenAPI and align comparisons/filters per field.
3. **Fix the suspicious activity-files call.** `app/pages/activities/index.vue` calls
   `/api/activities/search/findAllByActivityId?activityId=` (Spring-Data-REST style) — verify
   against the real `ActivityController` route and correct it.
4. **Reconcile UI permission keys with the backend registries.** The nav guesses some keys:
   Organizations is gated on `contacts.persons` (should be `contacts.organizations`), and
   `settings.automation.data_transfer` / `settings.user.*` need checking against
   `CrmPermissionRegistry`, `SettingsPermissionRegistry`, `IdentityPermissionRegistry`,
   `InventoryPermissionRegistry`, `SharingPermissionRegistry`. Export the canonical key list
   (or read from `/v3/api-docs`) and align.
5. **`Person.fullName` / `User.active`** — stop recomputing `fullName` client-side if the
   backend already returns it; add the missing `User.active` type. Resolved by task 1.

---

## Part 2 — The Component & Composable Library (Phase F0)

Build these, then refactor existing pages onto them. Names follow `web/CLAUDE.md`
conventions (PascalCase components in `app/components/`, `use*` composables).

### Composables (`app/composables/`)
| Composable | Responsibility | Consumers |
|---|---|---|
| `useListState` | page + PAGE_SIZE + debounced search + filters + selection + `queryKey` + refresh | ~18 list pages |
| `useCrud` | typed fetch/create/update/delete with loading + error→toast | ~25 pages |
| `useFormModal` | open/close + form model + submit/validation state | ~15 pages |
| `useTableColumns` | column-def builder (accessors, headers, row-actions slot) | ~15 pages |
| *(exists)* `useApi`, `usePermissions`, `useFormatters`, `useMassSelect`, `useDownload`, `useTheme` | keep; fold `useMassSelect` into `useListState` | — |

### Components (`app/components/`)
| Component | Replaces (verbatim dup) | Used by |
|---|---|---|
| `AppPageHeader` | title + actions row | ~30 pages |
| `AppDataTable` | `UTable` + select/loading/sticky/row-actions boilerplate | ~22 pages |
| `AppFilterBar` | search input + filter dropdowns | ~18 pages |
| `AppPagination` | `UPagination` + total/range display | ~15 pages |
| `AppRowActions` | View/Edit/Delete `UDropdownMenu` | ~20 pages |
| `AppDeleteConfirm` | delete `UModal` + state | ~20 pages |
| `AppEmptyState` | icon + message + CTA | ~20 pages |
| `AppFormModal` / `AppFormDrawer` | create/edit `UModal`+`UCard` shell | ~15 pages |
| `AppMassActionBar` | bulk delete/update bar | ~12 pages |
| `AppDetailLayout` | detail header + metadata sidebar (created/updated/owner) | ~5 detail pages |
| `AppTagManager` | tag search/add/remove | leads, person, org |
| `AppKanbanBoard` | column + drag/drop | leads (+future) |
| `AppStatusBadge` | enum→label+color mapping | most pages |

### Constants (`app/utils/` or `app/constants/`)
`PAGE_SIZE`, status enum→label/color maps, icon map, toast helpers — eliminate the
magic numbers/strings duplicated per page.

### F0 acceptance
Refactor **three reference pages first** (`leads/index.vue`, `contacts/persons/index.vue`,
`quotes/index.vue`) entirely onto the library, prove the pattern, then roll out across the
remaining ~33 pages. Target ~40% LOC reduction.

---

## Part 3 — Backend Coverage Gaps (Phase F2)

Current coverage ≈70% of controllers. Build in this priority order:

### P1 — Cross-tenant Sharing UI (the USP — **0% built today**)
The differentiator vs Krayin/Octolane has **no frontend**. Build pages for:
- `RecordShareController` — share a record with another tenant (grant/revoke).
- `SharePolicyController` — manage share policies.
- `RelationshipController` — cross-tenant relationships.
- `CrossTenantAuditController` — audit log viewer.
Add a top-level **"Sharing"** nav section. This is the highest-value net-new work.

### P2 — Complete the ERP/inventory story
- Warehouse **detail/edit** (list-only today) + locations.
- `InventoryMovementController` — movements ledger view (reserve/release).
- `TransferController` — transfer orders (create → dispatch → receive → cancel).

### P3 — Finish partial Settings/admin flows
- **Edit** flows for Users, Roles, Groups (create/delete exist; edit missing).
- **Pipelines** CRUD UI (currently lookup-only) incl. stage reorder.
- `SystemConfigController` — per-tenant config screen.
- `TenantController` — tenant management.
- `DataGridFilterController` — surface saved filters in list pages.
- Detail/run-history views for Workflows, Webhooks (+ "test" button), Marketing, Imports
  (beyond the current list-only pages).

### P4 — Dashboard
Build out `index.vue` beyond stats: the 8 dashboard stat types + recent activity, gated by
`reports.view`.

---

## Part 4 — Foundational Hardening (Phase F3, after F0–F2)

- **401 refresh single-flight.** `useApi` can fire concurrent refreshes; add a shared
  in-flight promise so parallel 401s await one refresh (prevents race + refresh storms).
- **Validation layer.** Add schema validation (Zod/Valibot) with **field-level errors** and
  **server-error mapping** (e.g. surface backend `ProblemDetail`/409 "email exists" on the
  field, not just a generic toast). `@nuxt/ui` `UForm` supports schema resolvers.
- **Granular errors + boundary.** Differentiate 400/403/404/500; add an error boundary so an
  unhandled error doesn't blank the page.
- **Mobile tables.** Card/stacked layout for `AppDataTable` under `md` (sticky-scroll is poor
  on touch).
- **Stores/caching only where it pays.** Keep Pinia light; add a domain store/cache only for
  hot, cross-page data (e.g. lookups: pipelines, sources, types, users). Avoid premature
  global state.
- **Tests + CI.** Playwright coverage raised (12 specs incl. auth, leads CRUD, quote send,
  sharing, inventory, export) — **verified green on 2026-06-07: 31 passed / 11 skipped / 0 failed**
  against the live stack (skips are honest: the empty seed DB has no rows for the data-dependent
  detail/mass-ops tests). **CI workflow NOT yet committed** — `.github/workflows/` does not exist in
  the repo. The drift *mechanism* is real and passes locally (`OpenApiSpecDriftTest` +
  `dumpOpenApiSpec` Gradle task; `api-docs.json` is in sync), but the GitHub Actions job that would
  run it (plus `web` checks and `api` build) has never been landed — it was blocked on the
  `workflow` OAuth scope from web sessions. The ready-to-paste YAML lives in `NEXT.md`. **Remaining:
  commit `.github/workflows/ci.yml`** and wire the e2e suite into it against a Testcontainers API.

- **Self-serve signup (2026-06-07).** New public `/register` page → `POST /auth/register`
  (auto-login). Lets a company onboard with no pre-existing admin. Validation long-tail also
  extended onto inventory **stock**/**transfers** and settings **email-templates**/**web-forms**/
  **webhooks** via `useFormSubmit`. See `NEXT.md` for the full list + the remaining forms.

### Explicitly DEFERRED for MVP
i18n (English-only now; ~11k LOC extraction is post-MVP), heavy global caching, and **all
agentic/AI surfaces** (command bar, AI panels, Agent Inbox) → `FUTURE_AGENTIC_CRM.md`.

---

## Part 5 — Phasing & Sequencing

| Phase | Goal | Depends on | Rough size |
|---|---|---|---|
| **F0** Component library | Kill duplication; 3 reference pages refactored then roll-out | — | ~3–4 wk |
| **F1** Contract correctness | OpenAPI type gen, enum casing, perm keys, bad endpoint | — (parallel w/ F0) | ~3–5 days |
| **F2** Coverage gaps | Sharing UI (P1) → inventory (P2) → settings (P3) → dashboard (P4) | F0 library | ~2–3 wk |
| **F3** Hardening | refresh race, validation, errors, mobile, tests/CI | F0 | ~1–2 wk |

**Recommended start:** F1 task 1 (generate OpenAPI types) **and** F0 (library + 3 reference
pages) in parallel — they unblock everything and immediately reduce drift and duplication.

---

## Part 6 — Executing with AI coding agents

Mirror the backend working agreement:
1. **One component or one page per PR.** Small, reviewable, revertible.
2. **Definition of done:** `pnpm typecheck` clean · `pnpm lint` clean · `pnpm format:check`
   clean · no new `any` · each new shared component has ≥1 page refactored onto it in the
   same PR · new pages get a Playwright smoke test.
3. **Source of truth for the API is OpenAPI**, not assumptions — generate types, don't guess
   DTO shapes. For feature behavior, follow `krayin-features-analysis/*` (kept as the spec).
4. **Permission-gate every page and action** via `usePermissions().can(<real backend key>)`.
5. **Reuse before adding.** If a pattern exists in the library, use it; if a third page needs
   a new variant, extend the component, don't fork it.

---

## Type-safety pipeline (F1 — backend↔frontend sync)

**Status: generator adopted; drift *test* passes locally (NOT yet wired into CI — no
`.github/workflows/` in the repo); SDK migration unblocked (next).**

The backend↔frontend contract is generated, not hand-maintained:

- **Generator:** [Hey API](https://heyapi.dev) (`@hey-api/openapi-ts`), config in
  `web/openapi-ts.config.ts`. `pnpm openapi:gen` (auto-runs on install) reads the springdoc
  spec snapshot `api-docs.json` and emits `web/app/api/{types,zod}.gen.ts` (gitignored).
- **Types:** `types.gen.ts` is the DTO source of truth. Migrate a module off hand-written
  DTOs by re-exporting the generated types from its `~/types/*` file (bridge pattern, done for
  `app/types/inventory.ts`); page imports stay unchanged. Remaining hand-written DTOs in
  `~/types/*` are the migration backlog.
- **Validation:** `zod.gen.ts` carries the backend's bean-validation rules as Zod schemas;
  `useFormSubmit().validate(form, zCreateGroupRequest)` uses them directly (done for
  groups/roles). No more hand-duplicated field rules.
- **Client:** intentionally NOT generated — `useApi()` stays the HTTP layer (auth + 401 refresh).
  A typed SDK + Nuxt client (`@hey-api/sdk` + `@hey-api/client-nuxt`) was evaluated for full
  call-site type-safety but is **BLOCKED — see below.**

### SDK / typed-client migration — UNBLOCKED (the documented next step)

A full migration of all ~200 call sites onto a generated SDK (so query params/paths/responses
are typed at the call site, not just the DTO) is the logical next step. It was blocked on two
backend spec-quality issues; **both are now resolved (PR #61):**

1. ~~**Spring Data REST pollution.**~~ **RESOLVED** — `spring-boot-starter-data-rest` exposure
   was removed, so the spec no longer carries the ~408 machine-generated HAL operationIds
   (`getCollectionResourceGroupGet`, …). Verified: the snapshot now has **0** `…Resource…`
   operationIds.
2. ~~**No explicit `operationId`s.**~~ **RESOLVED** — `OpenApiConfig.stableOperationIds`
   (an `OperationCustomizer`) now names every endpoint `controllerCamel + MethodPascal`
   (`groupCreate`, `leadAttachTag`, …). Verified: **0** auto-numbered `_N` collision ids remain.

So the SDK rollout is now mechanical and is the next phase: configure `@hey-api/sdk` +
`@hey-api/client-nuxt`, point the Nuxt client's `onRequest`/`onResponseError` at `useApi()`
(or pass `useApi()` as its `$fetch`), then migrate call sites module by module. Until then we
stay on `useApi()` + generated **types/zod**.

> Note on the remaining hand-written `~/types/*` DTOs: they intentionally **narrow enum fields**
> (e.g. `ActivityResponse.type: ActivityType`, `QuoteResponse.status: QuoteStatus`) that the
> generated types emit as bare `string`, because those unions back the `Record<Enum, …>`
> label/colour maps. Blanket-bridging them would *downgrade* type safety, so each module's bridge
> must reconcile enums (keep the union, bridge the rest) — that's why this is incremental, not a
> sweep. Bridged so far: `inventory` (incl. `ProductResponse`/`WarehouseResponse`), `pipelines`,
> and the `Tag` types.

**Known drift — cleared:**
1. ~~`TagDto` vs `TagResponse`~~ **DONE** — unified: `~/types/leads` re-exports both generated
   types; `AppTagManager` widened to the minimal `TagDto` shape (a `TagResponse[]` is a
   structural superset, so it flows in), unblocking the `ProductResponse`/`WarehouseResponse`
   bridge (both now generated).
2. ~~`MovementResponse` hand-written~~ **DONE** (PR #63) — bridged once the spec was regenerated.

**The drift gate — mechanism DONE, CI wiring NOT YET.** `api/.../OpenApiSpecDriftTest` boots the
Testcontainers context, GETs `/v3/api-docs`, and (under `-Dopenapi.dump=true`, via the
`dumpOpenApiSpec` Gradle task) writes springdoc's **raw** output verbatim to `api-docs.json`. As of
2026-06-07 `api-docs.json` is in sync (the drift test passes inside the full suite). What's missing
is the CI job: the intended `.github/workflows/ci.yml` `drift-gate` job (run the task, then
`git diff --exit-code api-docs.json`) plus the `web` (typecheck · lint · format:check · build) and
`api` (`./gradlew build`, Testcontainers) jobs **has never been committed** — there is no
`.github/workflows/` directory. So the gate is only enforced by running the suite manually until the
workflow YAML (in `NEXT.md`) is landed.

## Appendix — Key file paths
- Foundation: `app/composables/useApi.ts`, `app/stores/auth.ts`, `app/layouts/default.vue`
  (nav lives here), `app/composables/usePermissions.ts`, `app/app.config.ts`.
- Highest-duplication pages (refactor templates): `app/pages/leads/index.vue` (540),
  `app/pages/leads/[id].vue` (744), `app/pages/activities/index.vue` (640),
  `app/pages/settings/attributes/index.vue` (546), `app/pages/warehouses/index.vue` (527).
- Types to regenerate: `app/types/*.ts`.
- Tests: `tests/e2e/*.spec.ts`, `tests/e2e/helpers/auth.ts`.
