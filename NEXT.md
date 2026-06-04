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
- **F2-P2 ERP/inventory** — ✅ done on `claude/epic-lovelace-ovRwf` / PR #54 (see below).
- **F3 hardening** — partial: single-flight token refresh ✅ (#52). CI workflow written but
  not pushable from web sessions (OAuth lacks `workflow` scope) — see #52 description for the
  YAML; add it manually or grant scope. Form validation + error boundary + mobile tables still TODO.

## Current phase: F2 — Coverage gaps → **P2 ERP/inventory** ✅ COMPLETE (PR #54)

The inventory story is built on the shared library, gated on verified backend keys. **Next phase is
F2-P3 (settings/admin edit flows)** — see the handoff prompt at the bottom.

Inventory endpoint reference (backend complete), under `/api/*`:
- Warehouses: `GET /warehouses` (paged), `GET/PUT /warehouses/{id}`, `POST /warehouses`,
  `DELETE /warehouses/{id}`, `POST /warehouses/mass-destroy`
- Locations: `GET/POST /warehouses/{id}/locations`, `PUT/DELETE /warehouses/{id}/locations/{locId}`
- Warehouse sub-resources: `GET /warehouses/{id}/products` (→ `WarehouseProductEntry[]`),
  `GET /warehouses/{id}/activities` (paged), `POST /warehouses/{id}/tags`, `DELETE …/tags/{tagId}`
- Movements/stock: `GET /inventory/stock?productId&warehouseId[&locationId]` (warehouseId required),
  `POST /inventory/reserve`, `POST /inventory/release`, `GET /inventory/low-stock`
- Transfers: `GET /inventory/transfers` (plain list), `POST /inventory/transfers`,
  `POST /inventory/transfers/{id}/dispatch|receive|cancel`

Perms: `warehouses.view|create|edit|delete`, `inventory.movements.view|create`,
`inventory.reorder.view`, `inventory.transfers.view|create|manage`.
Transfer state machine: create → `PENDING`; dispatch `PENDING`→`IN_TRANSIT`; receive
`IN_TRANSIT`→`COMPLETED`; cancel **only** from `PENDING` → `CANCELLED`.

### Checklist (this phase) — ✅ shipped in PR #54

- [x] `app/types/inventory.ts` — warehouse `tags`, `StockStateResponse`, `LowStockEntry`,
  `TransferStatus` (+ label/color maps), `TransferOrderResponse`
- [x] `useInventoryLookups` composable — products/warehouses/locations lookups (id→label + options)
- [x] `warehouses/create.vue` — name + description + contact fields → `POST /warehouses`
- [x] `warehouses/[id].vue` — detail/edit + locations (add/rename/remove) + per-product stock +
  tags (`AppTagManager`) + activities (`EntityTimeline`)
- [x] `warehouses/index.vue` — slimmed: dropped the bespoke locations/tags/activities modal (now on
  the detail page); row menu = View + Delete (~250 LOC removed)
- [x] `inventory/stock.vue` — product+warehouse(+location) stock query + Reserve/Release
- [x] `inventory/reorder.vue` — low-stock/reorder report
- [x] `inventory/transfers/index.vue` — list + create + dispatch/receive/cancel lifecycle
- [x] Nav: Stock, Reorder, Transfers added to the Inventory section

### Notes for the next dev

- The backend has **no raw "list movements" endpoint** — the ledger is modeled as the Stock +
  Reorder screens (stock state, reserve/release, low-stock). If an append-only movement log is
  wanted, it needs a new backend endpoint first.
- Warehouses are **not** a cross-tenant shareable resource type, so there's no Share action there.
- Pre-existing bug spotted (out of scope): `quotes/create.vue` casts `/api/products` and `/api/leads`
  as plain arrays, but both return `PageResponse` (`{ content }`), so those selects are likely empty
  at runtime. Quick follow-up: read `.content` (or use `usePaginatedList`/`useInventoryLookups`).

## After P2: remaining F2 / F3 (see FRONTEND_PLAN.md)

- F2-P3 settings: edit flows for users/roles/groups, pipelines CRUD (+ stage reorder), system config,
  tenant mgmt, datagrid saved filters, detail/run-history for workflows/webhooks/marketing/imports.
- F2-P4 dashboard: remaining stat types + recent activity (gated `reports.view`).
- F3: form validation (Zod/Valibot + field errors + server-error mapping), error boundary,
  mobile/stacked tables, light domain caching, Playwright coverage, land the CI workflow.

## Ready-to-paste prompt for the next session

> Continue the Synoptic Engine frontend. Read `web/CLAUDE.md`, `FRONTEND_PLAN.md`, and
> `NEXT.md` first. F2-P2 (ERP/inventory) is **complete** on PR #54 — once it's merged, sync `main`
> and branch from it to start **F2-P3 (settings/admin)**: the missing **edit** flows for Users,
> Roles and Groups (create/delete already exist), **Pipelines** CRUD incl. stage reorder
> (`PipelineController`/stages), per-tenant **system config** (`SystemConfigController`), **tenant
> management** (`TenantController`, perm `tenants.manage`), surfacing **saved datagrid filters**
> (`DataGridFilterController`) in list pages, and detail/run-history views for
> Workflows/Webhooks (+ "test")/Marketing/Imports. Reuse the shared library (`AppPageHeader`,
> `AppListTable`, `AppDetailLayout`, `AppConfirmModal`, `usePaginatedList`, `useDeleteResource`, …);
> permission-gate every action with a verified backend key
> (`grep -rhoE 'const val [A-Z_]+ = "[^"]+"' api/src/main/kotlin --include='*Permissions.kt'`);
> dropdown items use `onSelect` not `click`. Verify each page green
> (`pnpm typecheck && pnpm lint && pnpm format:check && pnpm build`) before committing one
> page/component per commit. Open ONE draft PR after first push, keep going to completion, and
> update NEXT.md's checklist + handoff prompt at the end.
