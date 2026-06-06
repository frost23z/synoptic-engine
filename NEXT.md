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
- **F3 hardening** — mostly done (mechanism on #56; rollout + cache + e2e + CI on **PR #57**):
  - single-flight token refresh ✅ (#52)
  - error boundary + fallback error page ✅ (#56)
  - mobile/stacked tables ✅ (#56, AppListTable auto-collapses under `md`)
  - form validation + server-error mapping ✅ — mechanism (#56) + **rolled out to all entity
    create/edit forms** (#57): leads/products/warehouses/quotes create, the persons/orgs/products/
    warehouses/leads detail edit modals, the quote send-mail modal, and settings sources/types.
  - light domain cache for hot lookups ✅ (#57) — `useDomainLookups` (pipelines/sources/types/users
    in shared `useState`, fetched once); consumed by leads create + leads detail.
  - Playwright coverage raised ✅ — dashboard + form specs (#56) + **auth / leads CRUD / quote send /
    sharing** (#57).
  - **CI workflow** — written (web checks + `api · testClasses`), still NOT pushable from web
    sessions (OAuth **and** the GitHub App token lack `workflow`/`workflows` perms; both `git push`
    and the contents/git-data APIs reject `.github/workflows/*` → 404). Updated YAML preserved below
    — add it manually or grant the scope.
  - remaining create/edit forms still on the old toast pattern (long tail) — see Remaining.

## Current phase: F3 hardening — core done on PR #57 (long tail + CI remain)

### Checklist — F3 finish (PR #57)

- [x] **`useDomainLookups`** — shared `useState` cache for pipelines (with embedded stages), lead
      sources, lead types, users; idempotent loaders + `nuxtApp`-scoped in-flight de-dupe;
      `*Options` / `defaultPipeline` / `userName`. Wired into leads create + leads detail.
- [x] **Validation rollout** — `useFormSubmit` + `validators` on: leads/products/warehouses/quotes
      **create**; persons/orgs/products/warehouses/leads **detail edits**; the quote **send-mail**
      modal; settings **sources / types / tags / pipelines / users (create+edit+password) / roles /
      groups**; and the sharing USP forms **`ShareRecordModal`** (share+reshare) + **relationships**
      request. All map ProblemDetail → fields (no generic toast).
- [x] **Contract fixes** (verified vs `api-docs.json`): `PageResponse` `.content` on leads/persons/
      orgs/products selects; stages from `pipeline.stages` (not the POST-only `/stages` 405);
      `ProductResponse.isActive` drift (badge + edit key); default pipeline via `isDefault`;
      quote `discount`/`tax`/`adjustment` always sent (all required).
- [x] **Playwright** — `07-auth`, `08-leads-crud`, `09-quote-send`, `10-sharing` (live-API, resilient).
- [ ] **CI** — `.github/workflows/ci.yml` written (web + `api · testClasses`) but **un-pushable**
      this session (no `workflow`/`workflows` scope on either OAuth push or the App API). YAML below.
- [ ] **Long tail** — remaining settings/sharing/inventory create-edit forms (see Remaining).

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

> Tried to land it from this session both ways and both were rejected: `git push`
> (`refusing to allow an OAuth App to create or update workflow … without workflow scope`)
> and the GitHub MCP contents/git-data APIs (`404` — the App installation token lacks
> `workflows: write`). Add this file from a local checkout, or grant the scope.

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

  api:
    name: api · compile + test classes
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: api
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25
      - uses: gradle/actions/setup-gradle@v4
      # Compiles main + test sources (no DB/containers needed) for a fast signal.
      - run: ./gradlew --no-daemon testClasses
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

- `useFormSubmit` + `validators` is now on every entity create/edit form plus the identity admin
  (users/roles/groups), lead config (sources/types/tags/pipelines) and sharing USP forms
  (`ShareRecordModal`, relationships). The codebase has **no `<UForm>` pages** — everything is a
  plain `<form>` binding `errors[field]` to `UFormField :error`, so follow those references when
  finishing the long tail (settings web-forms/email-templates/marketing/workflows/webhooks/imports/
  config/tenants/attributes, `pipelines/[id]` stage modals, inventory stock/transfers).
- For a single shared `errors` across two mutually-exclusive modals on one page (create + edit),
  reuse one `useFormSubmit` and call `clearErrors()` in each `open*` (see `settings/sources`).
- Backend error contract (`shared/web/GlobalExceptionHandler.kt`): 422 → ProblemDetail with an
  `errors` **map** (`field → message`, not `string[]`); 409/400 carry `detail` only (no field), hence
  `fieldHints` keyword-matching for things like duplicate-email.

### Domain cache (F3) — `useDomainLookups`

- Shared `useState`-backed cache for the hot lookups (pipelines **with embedded stages**, lead
  sources, lead types, users). Loaders are idempotent + de-duped via a `nuxtApp`-scoped in-flight
  map; `invalidate(...keys)` drops a list after a mutation. Exposes `*Options`, `defaultPipeline`,
  `userName(id)`. `loadUsers()` is gated by `users.view` — only call it behind a `can()` check.
- **Contract fixes shipped alongside the rollout (verified against `api-docs.json`):**
  `/api/leads`, `/api/products`, `/api/contacts/persons`, `/api/contacts/organizations` all return
  **`PageResponse`** (`{ content }`) — the create/detail pages cast them as arrays and got empty
  selects; now read `.content`. Lead stages read from the embedded `pipeline.stages` (the
  `/api/pipelines` list includes them) — `GET /api/pipelines/{id}/stages` is **POST-only (405)**.
  `ProductResponse.active` was drifted → the real field is **`isActive`** (the Active badge was
  always wrong and product edits sent the wrong key); product create/edit now send `isActive`
  (+ optional `reorderThreshold`). Default pipeline keys off `isDefault`, not the legacy `default`.

### Settings/admin (F2-P3, still relevant)

- **Verified-key gotchas** (backend uses these `@PreAuthorize` keys regardless of the registered
  parent): pipeline edit/delete/stages → `leads.edit`; workflow/webhook/marketing **delete** →
  `*.edit` (not `*.delete`); import lifecycle/delete → `imports.edit`. Always grep the controller,
  not just `*Permissions.kt`.
- **Kotlin/Jackson dual-boolean:** response DTOs use `isActive`/`isDefault`/`isDone` (the `required`
  keys in the OpenAPI spec). Old hand-written types used `active`/`default`/`done` — align to the spec.
- **Pre-existing contract bugs — FIXED in #57:** the `GET /api/pipelines/{id}/stages` 405 (now read
  the embedded `pipeline.stages`) in `leads/create|[id]`; `leads/create` `p.default` → `isDefault`;
  the `PageResponse`-as-array empty selects in `leads/create`, `leads/[id]`, `persons/[id]`,
  `quotes/create`; and the `ProductResponse.active` → `isActive` drift on products list/detail/create.
  - Still **not** verified: `leads/index.vue` stage usage (didn't touch the kanban path this PR);
    `web-forms/index.vue` `active`/`predefined` field names — check against the WebForm DTO.

## Remaining

- **Validation long tail:** the remaining create/edit forms not yet converted — settings
  `pipelines/[id]` (add/edit-stage + edit-pipeline modals), `web-forms`, `email-templates`,
  `marketing`, `workflows` (+`[id]`), `webhooks` (+`[id]`), `imports`, `config`, `tenants`,
  `attributes`; inventory `stock` + `transfers`; `quotes/[id]` line-item edit. Same plain-`<form>` +
  `:error` pattern as the converted pages; reuse one `useFormSubmit` with `clearErrors()` for paired
  create/edit modals (see `settings/groups`/`roles`), or several scoped instances when a page has
  multiple independent forms (see `settings/users`).
- **CI:** land `.github/workflows/ci.yml` (YAML above) — blocked on the `workflow`/`workflows` scope
  from web sessions; add it from a local checkout or grant the scope. Then watch the first run (the
  `api · testClasses` job needs Temurin **JDK 25**; no foojay resolver, so `setup-java` must supply it).
- **Domain cache adoption:** more pages can drop their bespoke pipeline/source/type/user fetches onto
  `useDomainLookups` (e.g. `leads/index` filters, `quotes`/owner reassignment) — left as follow-up.
- **Deferred for MVP** (per FRONTEND_PLAN): i18n, heavy global caching, all agentic/AI surfaces.

## Ready-to-paste prompt for the next session

> Continue the Synoptic Engine frontend. Read `web/CLAUDE.md`, `FRONTEND_PLAN.md`, and `NEXT.md`
> first. The F3 core shipped on **PR #57** (merge it, then sync `main` and branch from it):
> `useFormSubmit`+`validators` is on every entity create/edit form, `useDomainLookups` is the shared
> hot-lookup cache (pipelines-with-stages/sources/types/users), e2e covers auth/leads-CRUD/quote-
> send/sharing, and several real contract bugs are fixed (see "Domain cache" notes). **Finish the
> long tail:** (1) roll `useFormSubmit` onto the remaining create/edit forms listed under
> "Remaining" (settings tags/pipelines/users/roles/groups/web-forms/email-templates/marketing/
> workflows/webhooks/imports/config/tenants/attributes, sharing relationships + `ShareRecordModal`,
> inventory stock/transfers) — plain `<form>` + `UFormField :error`, reusing one `useFormSubmit`
> with `clearErrors()` for paired create/edit modals (see `settings/sources` as the reference);
> (2) **land CI** — `.github/workflows/ci.yml` (full YAML in NEXT.md "CI workflow") could NOT be
> pushed this session (OAuth `git push` AND the GitHub App contents/git-data APIs both reject
> workflow files for lack of the `workflow`/`workflows` scope) — add it from a local checkout or
> grant the scope, then confirm the `api · testClasses` job gets Temurin JDK 25; (3) optionally
> adopt `useDomainLookups` on more pages (leads/index filters, owner reassignment). Verify the exact
> backend contract against `api-docs.json` (`PageResponse` vs array; `isActive`/`isDefault` not
> `active`/`default`; stages live on `pipeline.stages`, `/pipelines/{id}/stages` is POST-only) rather
> than guessing; permission-gate with a verified backend key (`grep -rhoE 'const val [A-Z_]+ =
> "[^"]+"' api/src/main/kotlin --include='*Permissions.kt'`, and grep the controller for the real
> `@PreAuthorize`); dropdown items use `onSelect` not `click`. Verify green (`pnpm typecheck && pnpm
> lint && pnpm format:check && pnpm build` — run serially, not alongside a background build) before
> committing one page/component per commit. Open ONE draft PR after first push, keep going, and
> update NEXT.md's checklist + handoff prompt at the end.
