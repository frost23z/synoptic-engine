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

- Base URL: `http://localhost:8090/api`
- Auth: `POST /auth/login` → `{ accessToken, refreshToken }`
- Header: `Authorization: Bearer <accessToken>` (15-min TTL)
- Refresh: `POST /auth/refresh`
- Full API spec: see `../GUIDELINES.md` Section 4

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

## Full spec

See `../GUIDELINES.md` Section 8 for the complete page list, component breakdown, and state management plan.
