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

## Roadmap & feature spec

- Completion roadmap (component/composable library, coverage gaps, phasing, agent working
  agreement): [`../FRONTEND_PLAN.md`](../FRONTEND_PLAN.md).
- Per-module feature behavior (user stories, fields, business rules): the
  `../krayin-features-analysis/*.md` reference docs.
- Agentic/AI surfaces are post-MVP — see [`../FUTURE_AGENTIC_CRM.md`](../FUTURE_AGENTIC_CRM.md).

> Note: a `GUIDELINES.md` was referenced in earlier docs but never existed; the OpenAPI spec
> plus the documents above are the sources of truth.
