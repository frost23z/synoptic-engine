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

## Current phase: F2 — Coverage gaps → **P1 Cross-tenant Sharing UI** ✅ COMPLETE

P1 (the USP) is fully built on `claude/frontend-sharing-ui` / PR #53 — relationships, share
policies (create/edit/revoke), record share + reshare, shared-with-me, and the cross-tenant
audit viewer (record + owner/actor self-views), all permission-gated and "us vs them" aware.
**Next phase is F2-P2 (ERP/inventory)** — see the handoff prompt at the bottom.

Sharing endpoint reference (backend complete), under `/api/*`:
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

### Record-level sharing — ✅ shipped (PR #53)

- [x] `ShareRecordModal` — lists current shares (revoke via `DELETE /records/share/{id}`) + a
  form to share (`POST /records/share`): consumer tenant + access level + optional expiry/note.
  Gated `records.share`. `useTenantNames` made non-blocking so it's safe in the mounted modal.
- [x] Wired the **Share** action into all five detail pages: leads, quotes, products,
  contacts/persons, contacts/organizations (resourceType = plural path names).

### Reshare + polish — ✅ shipped (PR #53)

- [x] **Reshare** (`POST /api/records/reshare`, `records.reshare`) — `ShareRecordModal` gained a
  `mode: 'share' | 'reshare'` prop (reshare hides the owner-scoped current-shares list and POSTs
  the reshare endpoint). A **Reshare** row action on `sharing/shared-with-me.vue` shows only for
  records shared to us with MANAGE; the action column appears only with `records.reshare`.
- [x] **Share-policy edit** (`PUT /share-policies/{id}`) — Edit action on each policy in
  `relationships/[id].vue`; reuses the add/edit modal. `resourceType` + `materialize` are
  immutable, so they're disabled in edit mode and omitted from the update body.
- [x] **Session tenantId** — added `tenantId` to backend `MeResponse` + `TokenResponse`
  (populated from the principal/credentials), threaded into `AuthUser` + the auth store, and
  exposed as `sessionTenantId` / `isSelf(id)` from `useTenantNames`. **Backend change** — the
  committed `api-docs.json` was regenerated to match.
- [x] **"Us vs them" relationship direction** — `relationships` list + detail render our side as
  "You" with an Incoming/Outgoing badge (falls back to source→target names if no session tenant).
- [x] **Audit owner/actor self-views** — `audit.vue` gained a scope selector: "Records I own"
  (`ownerTenantId=self`, the new default), "My cross-tenant activity" (`actorTenantId=self`), and
  the existing per-record trail. Self views mark our side as "You" and show a resource column.

## After P1: remaining F2 / F3 (see FRONTEND_PLAN.md)

- F2-P2 ERP/inventory: warehouse detail/edit + locations, inventory movements, transfer orders.
- F2-P3 settings: edit flows for users/roles/groups, pipelines CRUD, system config, tenant mgmt,
  datagrid saved filters, detail/run-history for workflows/webhooks/marketing/imports.
- F2-P4 dashboard: remaining stat types + recent activity (gated `reports.view`).
- F3: form validation (Zod/Valibot + field errors + server-error mapping), error boundary,
  mobile/stacked tables, light domain caching, Playwright coverage, land the CI workflow.

## Ready-to-paste prompt for the next session

> Continue the Synoptic Engine frontend. Read `web/CLAUDE.md`, `FRONTEND_PLAN.md`, and
> `NEXT.md` first. F2-P1 (Cross-tenant Sharing UI) is **complete** on PR #53 — once it's merged,
> sync `main` and branch from it to start **F2-P2 (ERP/inventory)**: warehouse detail/edit +
> locations, the inventory movements ledger (`InventoryMovementController`, reserve/release), and
> transfer orders (`TransferController`: create → dispatch → receive → cancel). Reuse the shared
> library (`AppPageHeader`, `AppListTable`, `AppDetailLayout`, `AppConfirmModal`, `usePaginatedList`,
> …); permission-gate every action with a verified backend key
> (`grep -rhoE 'const val [A-Z_]+ = "[^"]+"' api/src/main/kotlin --include='*Permissions.kt'`);
> dropdown items use `onSelect` not `click`. Verify each page green
> (`pnpm typecheck && pnpm lint && pnpm format:check && pnpm build`) before committing one
> page/component per commit. Open ONE draft PR after first push, keep going to completion, and
> update NEXT.md's checklist + handoff prompt at the end.
