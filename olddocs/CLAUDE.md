# CLAUDE.md — Synoptic Engine

## Project overview

CRM/ERP platform targeting Krayin CRM feature parity, plus unique cross-company resource sharing. Backend: Kotlin/Spring Boot 4. Frontend: Nuxt 4.

## Current status (2026-06-07, full re-verification)

- **Backend (`api/`): MVP complete + verified** — Krayin parity + enterprise-grade multi-tenant
  hardening (RLS, MFA, API keys, audit). Full Testcontainers suite green: **700 tests, 0 failures**
  (one stale test path fixed, see `BACKEND_PLAN.md`). 33 controllers, ~299 endpoints, migrations
  V001–V026.
- **Frontend (`web/`): MVP feature-complete; long-tail polish remains** — component library + all
  Krayin-parity pages + the cross-tenant Sharing USP are built (52 pages, 18 shared components).
  `typecheck`/`lint`/`format`/`build` clean; e2e green against the live stack (run `--workers=1` in
  the devcontainer — see `NEXT.md`). **Self-serve company signup** (`/register` → public
  `POST /auth/register`, auto-login) lets a new company onboard with no pre-existing admin.
  Remaining: validation long-tail on a few more settings forms, and **landing CI**
  (no `.github/workflows/` exists yet). See `FRONTEND_PLAN.md` / `NEXT.md`.
- **The MVP target — Krayin parity + cross-tenant resource sharing — is met and tested** on both
  stacks (sharing: `RecordShare`/`Relationship`/`SharePolicy`/`CrossTenantAudit` controllers + RLS
  + the `sharing/*` pages and `ShareRecordModal`).
- **Agentic / AI ("like Octolane"): future work, post-MVP** — see `FUTURE_AGENTIC_CRM.md`.

## Repository structure

```
synoptic-engine/
├── api/                        # Kotlin Spring Boot 4 REST API (CLAUDE.md = arch rules)
├── web/                        # Nuxt 4 frontend (CLAUDE.md = conventions)
├── krayin-features-analysis/   # Krayin CRM feature spec — reference for parity & frontend
├── krayin-reference/           # Krayin source reference
├── BACKEND_PLAN.md             # backend feature inventory & status (MVP complete)
├── FRONTEND_PLAN.md            # frontend completion roadmap (current phase)
└── FUTURE_AGENTIC_CRM.md       # post-MVP agentic/AI product plan
```

