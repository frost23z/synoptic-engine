# CLAUDE.md — Synoptic Engine Frontend

## Stack

- **Nuxt 4** (compatibilityVersion: 4, `app/` directory layout)
- **@nuxt/ui v4** — use components extensively; consult `@nuxt-ui-remote` MCP for API
- **Pinia** — state management
- **@vueuse/nuxt** — composables
- **TailwindCSS v4** — CSS-first config (no tailwind.config.js)
- **Tabler icons** via `@iconify-json/tabler` — prefix: `i-tabler-*`
- **TypeScript** strict

## Commands

```bash
pnpm dev            # dev server on http://localhost:3000
pnpm build
pnpm typecheck
pnpm lint           # eslint
pnpm lint:fix
pnpm format         # prettier
pnpm format:check
```

## Backend API

- Base host: `http://localhost:8090` (set as `runtimeConfig.public.apiBase`).
  Resource endpoints live under `/api/*`; auth endpoints under `/auth/*`.
- Auth: `POST /auth/login` → `{ accessToken, refreshToken }`
- Header: `Authorization: Bearer <accessToken>` (15-min TTL)
- Refresh: `POST /auth/refresh`
- **API contract = the backend OpenAPI spec** at `http://localhost:8090/v3/api-docs`
  (Swagger UI: `/swagger-ui`). Generate types from it rather than hand-writing DTOs.

## Directory layout (Nuxt 4)

```
app/
├── assets/
├── components/
├── composables/
├── layouts/
├── middleware/
├── pages/
├── plugins/
└── app.vue
server/
public/
nuxt.config.ts
```

## Code conventions

- Components: PascalCase, single-file `.vue`
- Composables: `use` prefix, in `app/composables/`
- Stores: `use*Store` pattern with Pinia `defineStore`
- API calls: wrap in composables, never raw `$fetch` in components
- All pages authenticated by default via `auth` middleware (except login)
- **Row-action menus use `onSelect` (not `click`)** — `@nuxt/ui` v4 ignores `click` on
  `DropdownMenuItem`. Type action arrays as `DropdownMenuItem[][]`.

## Shared UI library

Reuse these before hand-rolling list/table/modal markup (extracted to remove the ~65–75%
page duplication). Components are auto-imported.

**Components** (`app/components/`):

- `AppPageHeader` — title + subtitle + `#actions` slot.
- `AppListTable` — `UCard`+`UTable` wrapper; `selectable` injects a checkbox column and emits
  `update:selected` (pass `:selected`). Forwards all `*-cell` / `#empty` slots to `UTable`.
- `AppRowActions` — `:items="DropdownMenuItem[][]"` ⋮ menu.
- `AppMassActionBar` — bulk bar; renders when `:count > 0`; emits `clear`; default slot for actions.
- `AppPagination` — `v-model:page` + `:total` (+ optional `:page-size`); hides when one page.
- `AppEmptyState` — `:icon` + `:message` + default slot (CTA).
- `AppConfirmModal` — `v-model:open` + `:title` + `@confirm`; body in the default slot. Also
  backs create/edit/upload form modals via `:confirm-disabled` and `width-class`
  (e.g. `sm:max-w-2xl`).

**Composables** (`app/composables/`):

- `usePaginatedList<T>(endpoint, { key, params, searchParam, pageSize })` — `await`-ed; returns
  `{ page, search, items, total, pending, refresh }`. Handles 0-based↔1-based paging.
- `useDeleteResource<T>({ endpoint, successMessage, onDeleted })` — returns
  `{ open, target, deleting, prompt, confirm }`; pair with `AppConfirmModal`.
- `useMassSelect`, `useApi`, `usePermissions`, `useFormatters`, `useDownload`, `useTheme`.
- `useFormSubmit({ failureTitle })` — tracks `submitting`, maps backend ProblemDetail (422
  field errors / 409 `fieldHints`) onto `errors`, and validates forms. `validate(state, schema)`
  accepts **either a Zod schema** (preferred for new forms — `z.object({ … })`) or the legacy
  `Record<string, Validator[]>` map from `~/utils/validators`. Bind `errors[field]` to
  `UFormField :error`. Reference: `settings/groups/index.vue`, `settings/roles/index.vue`.

Reference implementations: `app/pages/leads/index.vue`, `contacts/persons/index.vue`,
`quotes/index.vue`. Shared constants (`PAGE_SIZE`, …) in `app/utils/constants.ts`.

**Typed API:** `pnpm openapi:types` generates `app/types/api.gen.ts` from `../api-docs.json`
(gitignored; auto-generated on install). Prefer generated types over hand-written DTOs.

### Rollout status

**All list/index pages are on the library** (leads, contacts persons/organizations, quotes,
products, activities, warehouses, and every settings list). Bespoke sub-panels (kanban,
pipeline stages, warehouse locations, attribute options, activity files/participants, import
stats) intentionally stay custom inside library-wrapped pages.

**Not yet migrated (bespoke, low list-duplication — optional follow-up):** detail pages
(`*/[id].vue`), create pages (`*/create.vue`), `mail/*`, and the dashboard (`pages/index.vue`).
A future `AppDetailLayout` (back button + title + actions + content) would standardise the
detail/create headers; until then they remain hand-rolled.

## Roadmap & feature spec

- Completion roadmap (component/composable library, coverage gaps, phasing, agent working
  agreement): [`../FRONTEND_PLAN.md`](../FRONTEND_PLAN.md).
- Per-module feature behavior (user stories, fields, business rules): the
  `../krayin-features-analysis/*.md` reference docs.
- Agentic/AI surfaces are post-MVP — see [`../FUTURE_AGENTIC_CRM.md`](../FUTURE_AGENTIC_CRM.md).

> Note: a `GUIDELINES.md` was referenced in earlier docs but never existed; the OpenAPI spec
> plus the documents above are the sources of truth.
