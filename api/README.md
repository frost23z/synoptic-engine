# Synoptic Engine — API (`api/`)

Kotlin + **Spring Boot 4** modular monolith (Spring Modulith) for a CRM/ERP platform:
Krayin CRM feature parity + cross-company resource sharing. PostgreSQL with Row-Level
Security; Flyway-owned schema; JWT + API-key auth.

> **Source of truth for architecture rules is [`CLAUDE.md`](./CLAUDE.md).** This README is
> just orientation — when in doubt, `CLAUDE.md` wins.

## Status (2026-06)

MVP backend is **complete**: Krayin parity + enterprise hardening (MFA, API keys, audit,
login history, password policy, RLS + Hibernate-filter tenant isolation). Compiles clean
(`./gradlew testClasses`). See [`../BACKEND_PLAN.md`](../BACKEND_PLAN.md) for the full feature
inventory and [`../FRONTEND_PLAN.md`](../FRONTEND_PLAN.md) for the current (frontend) phase.
Agentic/AI direction is future work — [`../FUTURE_AGENTIC_CRM.md`](../FUTURE_AGENTIC_CRM.md).

## Stack

- Kotlin 2.3 · Spring Boot 4.0 / Spring Framework 7 · Hibernate 7 (JPA, `ddl-auto=validate`)
- PostgreSQL + Flyway migrations (next file: **V027**) · RLS + Hibernate `@Filter` (two-layer tenancy)
- jjwt 0.13 (JWT) · API keys (`sk_` prefix) · Jackson 3 (`tools.jackson`)
- springdoc-openapi 3 — Swagger UI `/swagger-ui`, docs `/v3/api-docs`
- Testcontainers (PostgreSQL) for integration tests
- Anthropic Java SDK (one AI endpoint today; see `FUTURE_AGENTIC_CRM.md`)

## Commands

```bash
cd api
./gradlew testClasses   # fast compile check (no Docker)
./gradlew unitTests     # pure unit tests (no Spring/Testcontainers)
./gradlew test          # full suite — needs Docker (Testcontainers + non-superuser RLS role)
./gradlew bootRun       # start API on :8090
```

## Key runtime config (`application.yaml`)

`server.port=8090` · `api.base-path=/api` (auth is under `/auth`, not `/api`) · JWT 15-min
access / 7-day refresh · CORS `CORS_ALLOWED_ORIGINS` (defaults to localhost:3000).
Required in non-dev profiles (enforced by `SecretsGuard`): `JWT_SECRET`,
`SYNOPTIC_ENCRYPTION_KEY` (base64 32-byte). Optional: `ANTHROPIC_API_KEY` (AI lead extract).

## Package layout

`com.synopticengine.api` — modules: `auth`, `identity`, `crm` (lead, contact, activity,
email, quote, tag, dashboard, datagrid), `inventory` (product, warehouse, movement, transfer,
reorder), `settings` (attribute, automation, emailtemplate, marketing, imports, webform,
config), `sharing`, `dashboard`, `shared` (cross-cutting). See [`CLAUDE.md`](./CLAUDE.md) for
the full per-module conventions.
