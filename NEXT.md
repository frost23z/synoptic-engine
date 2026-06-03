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
- **F3 hardening** — partial: single-flight token refresh ✅ (#52). CI workflow written but
  not pushable from web sessions (OAuth lacks `workflow` scope) — see #52 description for the
  YAML; add it manually or grant scope. Form validation + error boundary + mobile tables still TODO.

## Current phase: F2 — Coverage gaps → **P1 Cross-tenant Sharing UI** (the USP)

Branch: `claude/frontend-sharing-ui`. Backend is complete; endpoints under `/api/*`:
- Relationships: `GET /relationships`, `GET/PATCH /relationships/{id}` (+ `/accept|/revoke|/suspend|/resume`), `POST /relationships`
- Share policies: `GET/POST /relationships/{relationshipId}/policies`, `GET/PUT/DELETE /share-policies/{id}`
- Record shares: `POST /records/share`, `POST /records/reshare`, `DELETE /records/share/{id}`,
  `GET /records/{resourceType}/{resourceId}/shares`, `GET /records/shared-with-me`
- Audit: `GET /cross-tenant-audit` (params: resourceType+resourceId, OR ownerTenantId==self, OR actorTenantId==self)
- Tenants (names): `GET /api/tenants` (perm `tenants.view`)

Perms: `relationships.view|manage`, `share-policies.view|manage`, `records.share|reshare`.
Enums: RelationshipType `PARENT_CHILD|PARTNER|SUPPLIER_CLIENT`; RelationshipStatus
`PENDING|ACTIVE|SUSPENDED|REVOKED`; AccessLevel `NONE|READ|COMMENT|WRITE|MANAGE`;
CrossTenantAction `VIEW|EDIT|COMMENT|DELETE|SHARE|RESHARE|REVOKE`. Resource types are the
plural path names: `leads|persons|organizations|quotes|products`.

### Checklist (this phase) — ✅ shipped in PR #53

- [x] `app/types/sharing.ts` (DTOs + label/color maps)
- [x] `useTenantNames` composable
- [x] Nav: "Sharing" section (Relationships, Shared with me, Audit) + Sign-out `onSelect` fix
- [x] `sharing/relationships/index.vue` — list + request-relationship modal
- [x] `sharing/relationships/[id].vue` — detail + lifecycle (accept/revoke/suspend/resume) + policies (list/create/revoke)
- [x] `sharing/shared-with-me.vue` — records other tenants shared with us
- [x] `sharing/audit.vue` — record-scoped cross-tenant audit viewer

### NEXT UP (start here): record-level sharing on entity pages

This is the remaining Sharing piece and the natural next branch (`claude/frontend-record-sharing`):
- **`ShareRecordModal` component** — pick consumer tenant (from `useTenantNames`) + access level
  (`GRANTABLE_ACCESS_LEVELS`) + optional expiry + note; `POST /api/records/share`. Types/maps
  already exist in `app/types/sharing.ts`.
- **`RecordSharesPanel` (or inline)** — `GET /api/records/{resourceType}/{resourceId}/shares`,
  list consumer tenant + access + expiry, revoke via `DELETE /api/records/share/{id}`
  (`useDeleteResource`), all gated `records.share`.
- Wire a **"Share" action** into the detail pages — `leads/[id]` (`leads`), `quotes/[id]`
  (`quotes`), `contacts/persons/[id]` (`persons`), `contacts/organizations/[id]`
  (`organizations`), `products/[id]` (`products`). resourceType = the plural names.
- **Reshare** (`POST /api/records/reshare`, `records.reshare`) — for records shared *to* us with
  MANAGE access; surface from `shared-with-me`.

### Smaller follow-ups

- **Share-policy edit** (`PUT /share-policies/{id}`) — only create+revoke shipped.
- **Audit "owner/actor" self-views** need the caller's tenantId, which `/auth/me` does NOT
  return today. Either (a) add `tenantId` to `MeResponse` + `AuthUser`/store (small backend
  change), then default the audit page to `ownerTenantId=self`; or keep it record-scoped.
- **"Us vs them" relationship direction** also needs session tenantId (currently shows
  source→target names via `GET /api/tenants`, `tenants.view`, with short-UUID fallback).

## After P1: remaining F2 / F3 (see FRONTEND_PLAN.md)

- F2-P2 ERP/inventory: warehouse detail/edit + locations, inventory movements, transfer orders.
- F2-P3 settings: edit flows for users/roles/groups, pipelines CRUD, system config, tenant mgmt,
  datagrid saved filters, detail/run-history for workflows/webhooks/marketing/imports.
- F2-P4 dashboard: remaining stat types + recent activity (gated `reports.view`).
- F3: form validation (Zod/Valibot + field errors + server-error mapping), error boundary,
  mobile/stacked tables, light domain caching, Playwright coverage, land the CI workflow.

## Ready-to-paste prompt for the next session

> Continue the Synoptic Engine frontend. Read `web/CLAUDE.md`, `FRONTEND_PLAN.md`, and
> `NEXT.md` first. Sync `main`, branch from it, and pick up the unchecked items in NEXT.md's
> current-phase checklist (or, if that phase is complete, start the next phase listed there).
> Reuse the shared library; permission-gate every action with a verified backend key
> (`grep -rhoE 'const val [A-Z_]+ = "[^"]+"' api/src/main/kotlin --include='*Permissions.kt'`);
> dropdown items use `onSelect` not `click`. Verify each page green
> (`pnpm typecheck && pnpm lint && pnpm format:check && pnpm build`) before committing one
> page/component per commit. Open ONE draft PR after first push, keep going to completion, and
> update NEXT.md's checklist + handoff prompt at the end.
