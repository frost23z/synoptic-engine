# Implementation Roadmap

## Overview

The roadmap is organized into phases. Phase 1 closes the CRM parity gaps. Phase 2 builds the unique cross-company sharing feature. Phase 3 delivers dashboard richness. Later phases expand toward ERP.

Each task is estimated with:
- **T-shirt size:** XS (<2h), S (2-4h), M (4-8h), L (1-2d), XL (2-4d)
- **Files to touch**
- **Testability**

---

## Phase 1 — Close CRM Parity Gaps (~2 weeks)

### Sprint 1a: Correctness Fixes

**P1.1 — Fix Activity Types** (S)
- `ActivityType.kt` — rename TASK to CALL/MEETING/LUNCH/NOTE/FILE
- `Activity.kt` — make `scheduleFrom`/`scheduleTo` nullable
- `ActivityService.kt` — auto-set `isDone=true` for NOTE; validate schedule fields
- `ActivityController.kt` — validation logic
- `V025__activity_missing_fields.sql`

**P1.2 — Activity: Add location and additional fields** (S)
- `Activity.kt` — add `location`, `additional`
- `ActivityDtos.kt` — add to request/response
- Migration in V025

**P1.3 — Activity: Person Participants** (M)
- `V026__activity_participant_persons.sql`
- New `ActivityParticipant.kt` embedded entity (user or person)
- Update `ActivityService.addParticipant()` to accept participant type
- Add `POST /activities/{id}/person-participants` endpoint

**P1.4 — Person: Multiple Emails and Phones** (M)
- `Person.kt` — change `email: String?` to `emails: String` (JSONB)
- `PersonDtos.kt` — update request/response
- `PersonService.kt` — parse/serialize JSON arrays
- `V027__person_json_contacts.sql`

**P1.5 — Pipeline: Rotten Days + Guards** (M)
- `Pipeline.kt` — add `rottenDays: Int?`
- `PipelineService.delete()` — guard against deleting default pipeline
- `PipelineService.delete()` — migrate leads to default pipeline first stage
- `PipelineService.update()` — migrate leads when stage deleted
- `V028__pipeline_quote_additions.sql`

---

### Sprint 1b: Business Rule Guards

**P1.6 — User Delete Guards** (XS)
- `UserService.delete()` — cannot delete last user, cannot delete self
- `UserService.massDestroy()` — filter self out, enforce minimum 1 user

**P1.7 — Role Delete Guards** (XS)
- `RoleService.delete()` — cannot delete if users assigned, cannot delete last role

**P1.8 — Person Delete Guard** (XS)
- `PersonService.delete()` — check `leadRepository.existsByPersonId(id)`

**P1.9 — Marketing Event Delete Guard** (XS)
- `MarketingService.deleteEvent()` — check if campaigns reference it

**P1.10 — Activity Note Auto-Done** (XS)
- `ActivityService.create()` — if `type == NOTE`, set `isDone = true`

---

### Sprint 1c: Missing Endpoints

**P1.11 — Quote: Billing/Shipping Addresses** (S)
- `Quote.kt` — add `billingAddress`, `shippingAddress` JSONB
- `QuoteDtos.kt` — add fields
- `V028` — add columns

**P1.12 — Quote: Lead-Product Sync** (M)
- `QuoteService.update()` — after saving quote items, sync to `lead_products` for linked lead

**P1.13 — Quote: Expired Filtering and Search** (S)
- `QuoteController.listAll()` — add `expiredOnly: Boolean` param
- `QuoteRepository.kt` — query with `expiredAt <= now()`
- Add `GET /quotes/search?q=` endpoint

**P1.14 — Dashboard: Full Stats** (L)
New `DashboardStatsController` with `GET /dashboard/stats?type=&startDate=&endDate=`:
- `over-all`: counts with previous period comparison
- `revenue-stats`: won vs lost revenue
- `total-leads`: time-series
- `revenue-by-sources`: GROUP BY source
- `revenue-by-types`: GROUP BY type
- `top-selling-products`: JOIN with lead_products
- `top-persons`: JOIN with persons
- `open-leads-by-states`: GROUP BY stage

**P1.15 — Web Form Public Submission** (M)
- `POST /public/web-forms/{formId}/submit` — no auth
- Create person from form attributes, optionally create lead
- Return success message or redirect URL

