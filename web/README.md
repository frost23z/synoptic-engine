# Synoptic Engine — Web (`web/`)

**Nuxt 4** + **@nuxt/ui v4** frontend for the Synoptic Engine CRM/ERP. TypeScript (strict),
Pinia, TailwindCSS v4, Tabler icons. Talks to the Spring Boot API on `:8090`.

> Conventions live in [`CLAUDE.md`](./CLAUDE.md). The completion roadmap (component library,
> coverage gaps, phasing) is [`../FRONTEND_PLAN.md`](../FRONTEND_PLAN.md). The API contract is
> the backend OpenAPI spec: `http://localhost:8090/v3/api-docs` (Swagger UI `/swagger-ui`).

## Status (2026-06)

Foundation in place (auth + cookie tokens with 401-refresh, permission-gated sidebar,
theme/dark-mode, ~36 pages over most modules). **In active completion**: extracting a shared
component/composable library to remove heavy duplication, generating types from OpenAPI, and
filling coverage gaps — notably the cross-tenant **Sharing** UI. See `../FRONTEND_PLAN.md`.

## Setup

```bash
cd web
pnpm install        # pnpm is the package manager (pnpm-lock.yaml committed)
```

## Commands

```bash
pnpm dev            # dev server on http://localhost:3000
pnpm build          # production build
pnpm preview        # preview the production build
pnpm typecheck      # vue-tsc type check
pnpm lint           # eslint   (pnpm lint:fix to autofix)
pnpm format         # prettier (pnpm format:check to verify)
pnpm exec playwright test   # e2e tests (needs the API running)
```

## Backend

- Base host: `http://localhost:8090` (`runtimeConfig.public.apiBase`).
- Resource endpoints are under `/api/*`; auth endpoints are under `/auth/*`.
- Wrap calls in `useApi()` (adds the bearer token + auto-refresh on 401) — never raw
  `$fetch` in components.

## Layout

`app/` (Nuxt 4): `pages/`, `components/`, `composables/`, `stores/`, `layouts/`,
`middleware/`, `plugins/`, `types/`, `assets/`. Tests in `tests/e2e/`.
