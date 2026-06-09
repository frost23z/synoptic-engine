# AGENTS.md — Synoptic Engine Coding Agent Guidelines

This file helps AI coding agents be productive in this repository. It is intentionally
concise and links to authoritative documentation for details.

## Project Snapshot

- Monolith CRM/ERP (Krayin parity + cross-company sharing). Backend (`api/`) is MVP-complete (full Testcontainers suite green: 700 tests/0 failures, 2026-06-07); frontend (`web/`) is MVP feature-complete and verified (typecheck/lint/format/build clean; e2e 31 passed/11 skipped/0 failed) with a validation long-tail + CI still to land.
- **No CI yet:** `.github/workflows/` does not exist. The drift test passes locally but the Actions workflow (ready YAML in [NEXT.md](NEXT.md)) is unlanded — the top remaining repo task.
- Status docs: [BACKEND_PLAN.md](BACKEND_PLAN.md), [FRONTEND_PLAN.md](FRONTEND_PLAN.md), [NEXT.md](NEXT.md)

## Must-Follow Rules — Backend (see [api/CLAUDE.md](api/CLAUDE.md))

- Money types: use `BigDecimal` (explicit scale & RoundingMode).
- Tenant isolation: two-layer — RLS + Hibernate `@Filter`.
- Entities extend `BaseEntity` (id, tenantId, timestamps, version).
- Soft-delete: respect `deletedAt` filtering.
- Use hand-written `@Query` (JPQL/native). Do not introduce `JpaSpecificationExecutor`.
- Pair Flyway migration + entity changes in the same commit.

## Must-Follow Rules — Frontend (see [web/CLAUDE.md](web/CLAUDE.md))

- Components: PascalCase single-file `.vue`.
- Composables: `use` prefix (e.g., `usePaginatedList`).
- API access: always use `useApi()` (handles auth + refresh). Avoid raw `$fetch` in components.
- Prefer generated types/schemas (`app/api/types.gen.ts`, `zod.gen.ts`) over hand-written types.
- Reuse shared components (`AppListTable`, `AppDetailLayout`, `AppConfirmModal`) before creating new ones.

## Dev Commands (quick reference)

Backend

```
cd api
./gradlew testClasses    # fast compile check
./gradlew unitTests      # unit tests (no Docker)
./gradlew test           # full suite (requires Docker/Testcontainers)
./gradlew bootRun        # run server on :8090
./gradlew dumpOpenApiSpec# update api-docs.json
```

Frontend

```
cd web
pnpm install
pnpm dev                 # dev server :3000
pnpm build               # production build (required for e2e)
pnpm test:e2e            # Playwright e2e (requires built output)
pnpm typecheck           # TS checks
pnpm lint:fix            # ESLint + Prettier
```

## Contracts & Feature Specs

- Backend OpenAPI: `http://localhost:8090/v3/api-docs` (regenerate with `./gradlew dumpOpenApiSpec`).
- Feature specs: [krayin-features-analysis/](krayin-features-analysis/) — consult before changing behavior.

## Dev-Environment Gotchas (brief)

- Full backend tests require Docker (Testcontainers). Use `unitTests` for fast feedback.
- Playwright e2e needs `pnpm build` and a built server (dev server is insufficient).
- Keep `api-docs.json` in sync: run `./gradlew dumpOpenApiSpec` after API changes and regenerate frontend types.

## When in Doubt

1. Check `krayin-features-analysis/` for the expected user behavior.
2. Check the backend OpenAPI (`/v3/api-docs`) for contract details.
3. Consult `api/CLAUDE.md` or `web/CLAUDE.md` for conventions.
4. If unsure about an architectural rule, ask a human and document the exception.

---

_This file is intentionally minimal — link to CLAUDE.md and the plans for implementation details._
