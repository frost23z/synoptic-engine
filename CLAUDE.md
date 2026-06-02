# CLAUDE.md — Synoptic Engine

## Project overview

CRM/ERP platform targeting Krayin CRM feature parity, plus unique cross-company resource sharing. Backend: Kotlin/Spring Boot 4. Frontend: Nuxt 4.

## Current status (2026-06)

- **Backend (`api/`): MVP complete** — Krayin parity + enterprise-grade multi-tenant
  hardening (RLS, MFA, API keys, audit). Compiles clean. See `BACKEND_PLAN.md`.
- **Frontend (`web/`): in active completion** — foundation built; refactoring onto a shared
  component library and filling coverage gaps. See `FRONTEND_PLAN.md`.
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

