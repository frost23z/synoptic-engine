# Synoptic Engine — Executive Summary

## What We're Building

A next-generation ERP/CRM platform that starts with full Krayin CRM parity but is built on a fundamentally different and more powerful architecture. The CRM is the foundation — we will layer ERP modules (inventory, purchasing, HR, accounting) on top as the platform matures.

## Why Users Should Choose Us Over Krayin or Other CRMs

### The Unique Differentiator: Cross-Company Resource Sharing

The problem we uniquely solve:

> Large organizations with a parent company and subsidiaries — or any two companies that need to collaborate — currently have no clean way to share CRM/ERP data. They either have to duplicate data across systems, grant full platform access (a security risk), or rely on manual exports. None of these scale.

**Our answer:** A first-class, permission-controlled resource sharing layer built into the core of the platform. Companies can share contacts, pipelines, products, price lists, and leads with fine-grained read/write controls — without merging tenants or compromising data sovereignty.

This is documented in full detail in `03-cross-company-sharing.md`.

### Additional Architectural Advantages Over Krayin

| Feature | Krayin | Synoptic Engine |
|---------|--------|-----------------|
| Authentication | Session-based, PHP | JWT, stateless, API-first |
| Primary keys | Integer auto-increment | UUID (globally unique, distributable) |
| Multi-tenancy | None (single-tenant) | Full tenant isolation from day 1 |
| Multiple roles per user | No (single role_id) | Yes (many-to-many user_roles) |
| Role granularity | Full tree or per-key | Same, with hierarchical inheritance |
| Technology | PHP / Laravel monolith | Kotlin / Spring Boot REST API |
| Data safety | Basic | Optimistic locking on all entities |
| Delete safety | Hard deletes mostly | Soft deletes on all core entities |
| Module isolation | Laravel packages (loose) | Strict module ports (CrmApi, IdentityApi, InventoryApi) |
| Event-driven | Laravel events (sync) | Spring domain events (async, @Async) |
| Extensibility for ERP | Very hard to add | Designed for it (module ports pattern) |

---

## Current State (as of May 2026)

### What's Implemented
- Full CRM backend API with ~90% Krayin feature parity (see `01-krayin-parity-status.md`)
- Multi-tenant architecture (tenant isolation on all domain tables)
- JWT auth + role-based access control
- All core CRM entities: Leads, Contacts (Persons + Orgs), Activities, Quotes, Email, Products, Warehouses, Tags, Pipelines, Attributes, Automation, Marketing, Web Forms, Data Imports

### What Needs Work
- ~10% remaining CRM parity gaps (see `02-crm-gaps-to-implement.md`)
- Cross-company sharing system (design complete, not yet implemented — see `03-cross-company-sharing.md`)
- Dashboard stats need expansion to Krayin parity level
- Workflow engine actions (only LOG is implemented)
- Permission key alignment with Krayin's full hierarchy

---

## Next Steps (in priority order)

1. **Phase 1 — Close CRM parity gaps** (~2 weeks) — see `02-crm-gaps-to-implement.md`
2. **Phase 2 — Build cross-company sharing** (~3-4 weeks) — see `03-cross-company-sharing.md`
3. **Phase 3 — Dashboard enhancements** (~1 week)
4. **Phase 4 — Workflow engine actions** (~1 week)
5. **Phase 5 — ERP foundation modules** (procurement, HR, basic accounting)

See `06-implementation-roadmap.md` for the full prioritized task breakdown.