# NEXT — Frontend working tracker

> Living doc: what's done, what's next, and a ready-to-paste prompt for the next
> session. Keep this up to date at the end of each session. Source of truth for
> sequencing is `FRONTEND_PLAN.md`; this tracks execution against it.

## Where we are

- **F0 component library** — ✅ done. List/index pages (#51) and detail/create/mail/dashboard
  pages (#52) all on the shared library (`AppPageHeader`, `AppListTable`, `AppDetailLayout`,
  `AppConfirmModal`, `AppTagManager`, `EntityTimeline`, `AppStatCard`, `usePaginatedList`,
  `useDeleteResource`, …).
- **F1 contract correctness** — ✅ done (OpenAPI type gen, enum casing, perm keys).
- **F2-P1 Cross-tenant Sharing UI** — ✅ done, merged (#53). Relationships, share policies, record
  share + reshare, shared-with-me, cross-tenant audit — all permission-gated and "us vs them" aware.
- **F2-P2 ERP/inventory** — ✅ done, merged (#54). Warehouse detail, stock, reorder, transfers.
- **F2-P3 settings/admin** — ✅ done, merged (#55) (see notes below).
- **F2-P4 dashboard** — ✅ done on `claude/zealous-gauss-ZoY6r` / **PR #56**: full Krayin-parity
  dashboard (the 8 stat types + recent activity), gated `reports.view`.
- **F3 hardening** — in progress (all on PR #56 unless noted):
  - single-flight token refresh ✅ (#52)
  - error boundary + fallback error page ✅
  - mobile/stacked tables ✅ (AppListTable auto-collapses under `md`)
  - form validation + server-error mapping ✅ (mechanism + Person/Org create as reference pages)
  - Playwright coverage raised ✅ (dashboard + form-validation specs)
  - **CI workflow** — written, still NOT pushable from web sessions (OAuth lacks `workflow`
    scope; the remote rejects `.github/workflows/*`). YAML preserved below — add it manually or
    grant the scope.
  - light domain caching for hot lookups — still TODO.

## Current phase: F2-P4 (dashboard) ✅ COMPLETE + F3 hardening (in progress) — PR #56

### Checklist — F2-P4 dashboard (PR #56)

- [x] **Backend contract verified, not guessed.** The 8 stat groups come from
      `GET /api/dashboard/stats?type=…` (`crm/dashboard/DashboardStatsController`): `over-all`,
      `revenue-stats`, `total-leads`, `revenue-by-sources`, `revenue-by-types`,
      `top-selling-products`, `top-persons`, `open-leads-by-states`. The endpoint returns `Any`,
      so it has **no typed schema in OpenAPI** → types in `app/types/dashboard.ts` are hand-mirrored
      from `DashboardStatsDtos.kt`. Recent/upcoming activity + top salespeople come from the
      `GET /api/dashboard` summary (`DashboardController`), which *is* in the spec.
- [x] **Fixed the activity contract bug:** the old inline interface used `done`; the DTO/spec field
      is `isDone` (so the done badge/strikethrough never rendered). `scheduleFrom/To` are optional.
- [x] **`useDashboardStats`** — reactive date range (presets + auto day/week/month bucket), fetches
      all 8 stat groups in parallel + the summary, gated on `reports.view` (`immediate: canView`).
- [x] **`index.vue`** — date-range control (presets + custom date inputs), 9 KPI tiles w/ trends,
      leads-over-time chart, open-leads/revenue-by-source/revenue-by-type bar lists, top products /
      customers / salespeople, live recent + upcoming activity. Permission-empty state when no
      `reports.view`.
- [x] **New shared library pieces:** `AppStatCard` extended with an optional trend pill;
      `AppBarList` (3 in-page consumers); `AppLineChart` (dependency-free SVG, theme-aware via
      `currentColor`). No new dependencies.

### Checklist — F3 hardening (PR #56)

- [x] **Error boundary** — `AppErrorState` (shared panel) + `app/error.vue` (fatal/404, clearError
      recovery) + `NuxtErrorBoundary` around the layout's main slot (inline fallback keeps the nav).
- [x] **Mobile tables** — `AppListTable` renders stacked label/value cards under `md` (reusing the
      same `*-cell` slots via a synthetic `{ row: { original } }`); every list page benefits with no
      page change.
- [x] **Form validation + server-error mapping** — `app/utils/validators.ts` (required/email/url/
      min/maxLength) + `useFormSubmit` (submitting + client `validate` + ProblemDetail mapping: 422
      `errors` → fields, `fieldHints` routes a 409/400 `detail` to a field, else toast). Reference
      pages: `contacts/persons/create.vue`, `contacts/organizations/create.vue`.
- [x] **Playwright** — `tests/e2e/05-dashboard.spec.ts` + `06-forms.spec.ts` (run against a live API).
- [ ] **CI workflow** — written but blocked on `workflow` OAuth scope (see YAML below).

### CI workflow (paste to `/.github/workflows/ci.yml` — needs `workflow` scope to push)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  web:
    name: web · typecheck / lint / format / build
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: web
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
        with:
          version: 11
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: pnpm
          cache-dependency-path: web/pnpm-lock.yaml
      # postinstall runs `nuxt prepare` + generates app/types/api.gen.ts from ../api-docs.json
      - run: pnpm install --frozen-lockfile
      - run: pnpm typecheck
      - run: pnpm lint
      - run: pnpm format:check
      - run: pnpm build
```

## Notes for the next dev

### Dashboard (F2-P4)

- The stats endpoint is **untyped on the wire** (`ResponseEntity<Any>`) — keep `app/types/dashboard.ts`
  in lockstep with `crm/dashboard/web/DashboardStatsDtos.kt` by hand; `pnpm openapi:types` will NOT
  generate these. `over-all`/`revenue-stats` deltas come from the backend (`changePercent`);
  `revenue-stats` has no `changePercent`, so the page computes it from `previous*` fields.
- `AppLineChart` is intentionally minimal (no axis/tooltip lib). Series colors are **Tailwind text
  classes** (`text-primary`/`text-success`/`text-error`) and the stroke uses `currentColor` so it
  stays theme-aware; `AppBarList` takes a raw CSS `color` for stage hexes.

### Validation (F3)

- Roll `useFormSubmit` + `validators` out to the **remaining create/edit forms** (only Person/Org
  create are done as references). For pages already using `UForm`, call `applyError(err, …)` then map
  `errors.value` onto `form.setErrors(...)` instead of binding `:error` manually.
- Backend error contract (`shared/web/GlobalExceptionHandler.kt`): 422 → ProblemDetail with an
  `errors` **map** (`field → message`, not `string[]`); 409/400 carry `detail` only (no field), hence
  `fieldHints` keyword-matching for things like duplicate-email.

### Settings/admin (F2-P3, still relevant)

- **Verified-key gotchas** (backend uses these `@PreAuthorize` keys regardless of the registered
  parent): pipeline edit/delete/stages → `leads.edit`; workflow/webhook/marketing **delete** →
  `*.edit` (not `*.delete`); import lifecycle/delete → `imports.edit`. Always grep the controller,
  not just `*Permissions.kt`.
- **Kotlin/Jackson dual-boolean:** response DTOs use `isActive`/`isDefault`/`isDone` (the `required`
  keys in the OpenAPI spec). Old hand-written types used `active`/`default`/`done` — align to the spec.
- **Pre-existing, out of scope (spotted, not fixed):**
  - `leads/index.vue`, `leads/[id].vue`, `leads/create.vue` call `GET /api/pipelines/{id}/stages`,
    but the custom `PipelineController` only exposes `POST` there → likely 405. Read stages from
    `GET /api/pipelines/{id}` (`.stages`) like the pipeline detail page does.
  - `leads/create.vue` reads `p.default` off `/api/pipelines` — use `isDefault`.
  - `quotes/create.vue` casts `/api/products` and `/api/leads` as arrays but both return
    `PageResponse` (`{ content }`) → selects likely empty.
  - `web-forms/index.vue` still uses `active`/`predefined` field names — verify against the WebForm DTO.

## Remaining

- **F3 finish:** roll validation out across the rest of the create/edit forms; add light domain
  caching for hot lookups (pipelines/sources/types/users); raise Playwright coverage further (auth,
  leads CRUD, quote send, sharing); **activate the CI workflow** (grant `workflow` scope or add the
  YAML above manually — consider adding a backend `./gradlew testClasses` job too).
- **Deferred for MVP** (per FRONTEND_PLAN): i18n, heavy global caching, all agentic/AI surfaces.

## Ready-to-paste prompt for the next session

> Continue the Synoptic Engine frontend. Read `web/CLAUDE.md`, `FRONTEND_PLAN.md`, and `NEXT.md`
> first. F2-P4 (dashboard) and most of F3 hardening (error boundary, mobile tables, validation +
> server-error mapping, dashboard/form Playwright specs) shipped on PR #56 — once it's merged, sync
> `main` and branch from it. **Finish F3:** (1) roll `useFormSubmit` + `app/utils/validators.ts`
> out to the remaining create/edit forms (only `contacts/persons|organizations/create.vue` are done
> as references; for `UForm`-based pages call `applyError` then map `errors.value` onto
> `form.setErrors`); (2) add a light domain cache (Pinia or `useState`) for hot lookups —
> pipelines/sources/types/users — fetched once and shared across pages; (3) raise Playwright
> coverage on critical paths (auth, leads CRUD, quote send, sharing) following
> `tests/e2e/helpers/auth.ts` + `getByText`/`pressSequentially` patterns; (4) **activate CI** — the
> workflow YAML is in NEXT.md under "CI workflow"; it can't be pushed from web sessions (OAuth lacks
> `workflow` scope), so add `.github/workflows/ci.yml` manually or grant the scope, and consider a
> backend `./gradlew testClasses` job. Verify the exact backend contract against the OpenAPI spec
> (`api-docs.json` / `/v3/api-docs`) rather than guessing; permission-gate with a verified backend
> key (`grep -rhoE 'const val [A-Z_]+ = "[^"]+"' api/src/main/kotlin --include='*Permissions.kt'`,
> and grep the controller for the real `@PreAuthorize`); dropdown items use `onSelect` not `click`.
> Verify green (`pnpm typecheck && pnpm lint && pnpm format:check && pnpm build`) before committing
> one page/component per commit. Open ONE draft PR after first push, keep going to completion, and
> update NEXT.md's checklist + handoff prompt at the end.
