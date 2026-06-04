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
- **F2-P3 settings/admin** — ✅ done on `claude/dreamy-shannon-3XURs` / PR #55 (see below).
- **F3 hardening** — partial: single-flight token refresh ✅ (#52). CI workflow written but
  not pushable from web sessions (OAuth lacks `workflow` scope) — see #52 description for the
  YAML; add it manually or grant scope. Form validation + error boundary + mobile tables still TODO.

## Current phase: F2 — Coverage gaps → **P3 settings/admin** ✅ COMPLETE (PR #55)

All settings/admin flows are built on the shared library, gated on the **verified backend
authority each endpoint actually checks** (which sometimes differs from the registered key —
documented inline per page). **Next phase is F2-P4 (dashboard)** then the rest of F3 — see the
handoff prompt at the bottom.

### Checklist (this phase) — ✅ shipped in PR #55

- [x] **Users** edit (name/phone/roles/groups/visibility) + set-password (`users.edit`);
      fixed `UserResponse` (`isActive`, not `active`) + added `UserDetailResponse`.
- [x] **Roles** edit (name/description/permissions); fixed permission grouping (`.`-delimited keys,
      not `:`); edit + delete gated on `roles.edit` (no `roles.delete` key exists).
- [x] **Groups** edit (`groups.edit`; delete `groups.delete`).
- [x] **Pipelines** list (create/delete) + `settings/pipelines/[id].vue` detail: edit pipeline and
      add/edit/delete/**drag-reorder** stages. Stages are read from `GET /pipelines/{id}` (embedded —
      the custom controller has **no** `GET .../stages`); reorder PUTs `{ order:[{id,sortOrder}] }`.
      Mutations gated on `leads.edit`, create on `pipelines.create`.
- [x] **System config** `settings/config` — grouped per-tenant config, type-aware inputs, batched
      save; secret items render blank ("set" hint) and only write when changed. View `settings.view`,
      save `settings.edit`.
- [x] **Tenant management** `settings/tenants` — list + provision (admin email/password), slug
      auto-derive + pattern validate. View `tenants.view`, provision `tenants.manage`.
- [x] **Saved datagrid filters** — `useSavedFilters` + `AppSavedFilters` popover wired into the
      Leads + Persons lists (apply/save/delete). View `datagrid-filters.view`, edit `datagrid-filters.edit`.
- [x] **Workflows** `settings/workflows/[id].vue` — run history (`/runs`) + rules + edit
      (name/event/AND-OR/active). Edit+delete `automations.edit`.
- [x] **Webhooks** `settings/webhooks/[id].vue` — delivery history (`/deliveries`) + **Test**
      (`POST /test`) + edit (secret blank ⇒ removed, per backend). Fixed create to send `isActive`.
- [x] **Marketing** event + campaign edit, campaign **execute** (recipients → sent/queued).
      Edit/delete/execute `marketing.edit`.
- [x] **Imports** `settings/imports/[id].vue` — lifecycle (validate→link→index→start) + stats +
      errors + CSV download (via `useDownload`). Start/lifecycle/delete `imports.edit`.
- [x] Nav: System Config + Tenants added to the Settings section.

### Notes for the next dev

- **Verified-key gotchas** (backend uses these `@PreAuthorize` keys regardless of the registered
  parent): pipeline edit/delete/stages → `leads.edit`; workflow/webhook/marketing **delete** →
  `*.edit` (not `*.delete`); import lifecycle/delete → `imports.edit`. Always grep the controller,
  not just `*Permissions.kt`.
- **Kotlin/Jackson dual-boolean:** response DTOs use `isActive`/`isDefault` (the `required` keys in
  the OpenAPI spec). The old hand-written types used `active`/`default` and several send-paths were
  wrong — all settings pages now align to the request/response DTOs.
- **Pre-existing, out of scope (spotted, not fixed):**
  - `leads/index.vue`, `leads/[id].vue`, `leads/create.vue` call `GET /api/pipelines/{id}/stages`,
    but the custom `PipelineController` only exposes `POST` there (the `GET` is a separate Spring
    Data REST route **without** the `/api` prefix) → likely 405 at runtime. Read stages from
    `GET /api/pipelines/{id}` (`.stages`) like the new pipeline detail page does.
  - `leads/create.vue` reads `p.default` off `/api/pipelines` — use `isDefault`.
  - `quotes/create.vue` casts `/api/products` and `/api/leads` as arrays but both return
    `PageResponse` (`{ content }`) → selects likely empty.
  - `web-forms/index.vue` still uses `active`/`predefined` field names — verify against the
    WebForm DTO when that page is next touched.

## Remaining: F2-P4 + F3 (see FRONTEND_PLAN.md)

- **F2-P4 dashboard:** build `pages/index.vue` beyond stats — the 8 dashboard stat types + recent
  activity, gated `reports.view` (`DashboardController` / `crm/dashboard`). `AppStatCard` already exists.
- **F3 hardening:** form validation (Zod/Valibot + field errors + server-error mapping), error
  boundary, mobile/stacked tables, light domain caching (lookups: pipelines/sources/types/users),
  raise Playwright coverage, land the CI workflow (needs `workflow` OAuth scope or manual add).

## Ready-to-paste prompt for the next session

> Continue the Synoptic Engine frontend. Read `web/CLAUDE.md`, `FRONTEND_PLAN.md`, and `NEXT.md`
> first. F2-P3 (settings/admin) is **complete** on PR #55 — once it's merged, sync `main` and branch
> from it to start **F2-P4 (dashboard)**: build out `app/pages/index.vue` beyond the current stats to
> the full dashboard — the 8 dashboard stat types + recent activity — gated on `reports.view`
> (`DashboardController`; stats live in `api/.../crm/dashboard`). Reuse the shared library
> (`AppPageHeader`, `AppStatCard`, `AppListTable`/`EntityTimeline`, `usePaginatedList`, …); confirm
> the exact stat payloads against the OpenAPI spec (`api-docs.json` / `/v3/api-docs`) rather than
> guessing. Permission-gate every widget with a verified backend key
> (`grep -rhoE 'const val [A-Z_]+ = "[^"]+"' api/src/main/kotlin --include='*Permissions.kt'`);
> dropdown items use `onSelect` not `click`. Verify green
> (`pnpm typecheck && pnpm lint && pnpm format:check && pnpm build`) before committing one
> page/component per commit. After P4, pick up the remaining F3 hardening (form validation +
> server-error mapping, error boundary, mobile tables, Playwright coverage, land the CI workflow).
> Open ONE draft PR after first push, keep going to completion, and update NEXT.md's checklist +
> handoff prompt at the end.
