# Krayin CRM Parity Status

Legend: ✅ Implemented | ⚠️ Partial | ❌ Missing

> Verified May 2026 against `api/src/main/kotlin/**` and `krayin-reference/**`. Notes call out drift between the analysis and what the code actually does.

---

## 1. Authentication

| Feature | Status | Notes |
|---|---|---|
| Login with email/password | ✅ | `POST /auth/login` returns access + refresh JWT |
| Logout | ✅ | `POST /auth/logout` (frontend clears tokens; no server-side token blacklist) |
| Remember me | ❌ | JWT is stateless; consider configurable refresh-token TTL |
| Forgot password (email token) | ✅ | `POST /auth/forgot-password` |
| Reset password via token | ✅ | `POST /auth/reset-password` |
| Account edit (name, email, password, image) | ⚠️ | `PUT /auth/account` exists; profile image upload not verified |
| Block inactive users on login | ✅ | `isActive` check in `AuthService.requireActive()` |
| JWT contains tenant_id | ❌ | **P0-1** in `07-verification-findings.md` — multi-tenancy not enforced per request |

---

## 2. Leads

| Feature | Status | Notes |
|---|---|---|
| List leads (paginated) | ✅ | `GET /api/leads` |
| Kanban view grouped by stage | ✅ | `GET /api/leads/kanban?pipelineId=` |
| Create lead | ✅ | `POST /api/leads` (gated on `leads.edit` instead of `leads.create` — see P1-2) |
| Create lead with inline person | ❌ | Must create person separately first |
| AI lead creation from file upload | ❌ | Krayin has LLM extraction; not planned |
| View lead detail | ✅ | `GET /api/leads/{id}` |
| Update lead | ✅ | `PUT /api/leads/{id}` |
| Move lead stage | ✅ | `PATCH /api/leads/{id}/stage` |
| Auto-set `closedAt` on won/lost | ⚠️ | Logic exists in `moveStage`; verify in `LeadService` |
| Search leads (autocomplete) | ✅ | `GET /api/leads/search` — **but unscoped (P1-1 leak)** |
| Delete lead | ✅ | `DELETE /api/leads/{id}` |
| Mass update leads (stage) | ✅ | `POST /api/leads/mass-update` |
| Mass delete leads | ✅ | `POST /api/leads/mass-destroy` |
| Attach/detach tags | ✅ | `POST/DELETE /api/leads/{id}/tags/{tagId}` |
| Attach/detach products | ✅ | `POST/DELETE /api/leads/{id}/products/{productId}` |
| List emails for lead | ✅ | `GET /api/leads/{id}/emails` |
| Attach/detach email | ✅ | `POST/DELETE /api/leads/{id}/emails/{emailId}` |
| Kanban look-up (filter dropdowns) | ❌ | Krayin has a dedicated endpoint; we don't |
| Rotten leads (rottenDays) | ❌ | Pipeline missing `rotten_days` column |
| View permission scoping | ⚠️ | `findAll/filter` scoped; `search`/`kanban` not |

---

## 3. Contacts — Persons

| Feature | Status | Notes |
|---|---|---|
| List persons (paginated) | ✅ | `GET /api/contacts/persons` |
| Create person | ✅ | `POST /api/contacts/persons` |
| View person detail | ✅ | `GET /api/contacts/persons/{id}` |
| Edit person | ✅ | `PUT /api/contacts/persons/{id}` |
| Search persons (autocomplete) | ✅ | `GET /api/contacts/persons/search` |
| Delete person | ✅ | `DELETE /api/contacts/persons/{id}` |
| Block delete if has leads | ❌ | No guard; FK behaviour not enforced at app layer |
| Mass delete persons | ✅ | `POST /api/contacts/persons/mass-destroy` |
| Partial-success mass delete | ❌ | All-or-nothing |
| Attach/detach tags | ✅ | `POST/DELETE /api/contacts/persons/{id}/tags/{tagId}` |
| Activities for person | ⚠️ | Via `GET /api/activities?personId=`; no dedicated route |
| Multiple emails JSON array | ❌ | Entity stores scalar `email` (P1-4) |
| Multiple contact numbers JSON array | ❌ | Entity stores scalar `phone` (P1-4) |
| Job title | ✅ | `jobTitle` on Person |
| View permission scoping | ✅ | Applied in `PersonService` |

---

## 4. Contacts — Organizations

| Feature | Status | Notes |
|---|---|---|
| List organizations | ✅ | `GET /api/contacts/organizations` |
| Create organization | ✅ | `POST /api/contacts/organizations` |
| Edit organization | ✅ | `PUT /api/contacts/organizations/{id}` |
| Delete organization | ✅ | `DELETE /api/contacts/organizations/{id}` |
| Mass delete | ✅ | `POST /api/contacts/organizations/mass-destroy` |
| Person count per org (DataGrid) | ❌ | Not computed in listing response |
| Address structured | ⚠️ | Currently scalar `address`; consider JSONB for parity |
| Search organizations | ⚠️ | Not exposed as dedicated endpoint |

---

## 5. Activities

| Feature | Status | Notes |
|---|---|---|
| List activities (paginated) | ✅ | `GET /api/activities` |
| Filter by entity (lead/person/etc.) | ✅ | Query params |
| Create activity | ✅ | `POST /api/activities` |
| Edit activity | ✅ | `PUT /api/activities/{id}` |
| Toggle done/not-done | ✅ | `PATCH /api/activities/{id}/done` |
| Mass mark done/undone | ✅ | `POST /api/activities/mass-update` |
| Mass delete | ✅ | `POST /api/activities/mass-destroy` |
| Delete activity | ✅ | `DELETE /api/activities/{id}` |
| Activity types match Krayin | ⚠️ | Has `CALL, EMAIL, MEETING, TASK, NOTE, MESSAGE`; missing `LUNCH`, `FILE` — see P1-3 |
| Auto-done for notes | ❌ | No special handling |
| Location field | ❌ | Not on entity |
| Additional JSON field | ❌ | Not on entity |
| Calendar view (date range) | ❌ | No endpoint |
| User participants | ✅ | `activity_participants.user_id` |
| Person participants | ❌ | Schema is user-only (P1-3) |
| Meeting overlap check | ❌ | No validation |
| File upload to activity | ✅ | `POST /api/activities/{id}/file` |
| File download | ✅ | `GET /api/activities/{id}/file/{fileId}/download` |

---

## 6. Mail / Email

| Feature | Status | Notes |
|---|---|---|
| List emails by folder | ✅ | `GET /api/mail?folder=` |
| View email thread | ⚠️ | `GET /api/mail/{id}` exists; thread loading not verified |
| Compose and send email | ✅ | `POST /api/mail` |
| Save as draft | ❌ | No draft mode |
| Edit and send draft | ❌ | No send-draft endpoint |
| Reply to email (parent_id) | ⚠️ | Field exists; thread queries not verified |
| Forward email | ❌ | No forward endpoint |
| Move email to folder | ✅ | `PATCH /api/mail/{id}/folder` |
| Multi-folder per email | ✅ | `folders` is JSONB array — matches Krayin (previous draft of the analysis missed this) |
| Mark read/unread | ✅ | `PATCH /api/mail/{id}/read` |
| Delete (trash / permanent) | ✅ | `DELETE /api/mail/{id}` |
| Mass move to folder | ✅ | `POST /api/mail/mass-update` |
| Mass delete | ✅ | `POST /api/mail/mass-destroy` |
| Attach/detach tags | ✅ | `POST/DELETE /api/mail/{id}/tags/{tagId}` |
| Download attachment | ✅ | `GET /api/mail/attachments/{id}/download` |
| Inbound parse webhook | ✅ | `POST /api/mail/inbound-parse` (public) |
| Per-folder permissions | ❌ | Single `mail.view`; Krayin has per-folder (P0-5) |
| Attachment upload on compose | ⚠️ | No multipart on compose; `attachmentIds` not accepted |

---

## 7. Quotes

| Feature | Status | Notes |
|---|---|---|
| List quotes | ✅ | `GET /api/quotes` |
| Create quote | ✅ | `POST /api/quotes` |
| Edit quote | ✅ | `PUT /api/quotes/{id}` |
| Delete quote | ✅ | `DELETE /api/quotes/{id}` |
| Mass delete | ✅ | `POST /api/quotes/mass-destroy` |
| Print / download PDF | ✅ | `GET /api/quotes/{id}/print` (openpdf) |
| Send quote by email | ✅ | `POST /api/quotes/{id}/send-mail` |
| Get lead products as quote items | ✅ | `GET /api/quotes/lead-products/{leadId}` |
| Duplicate quote | ✅ | `POST /api/quotes/{id}/duplicate` (beyond Krayin) |
| Billing / shipping address | ❌ | Not on entity (P1-6) |
| `personId` on quote | ❌ | Not on entity (P1-6); Krayin requires NOT NULL |
| Quote item: discount, tax per line | ⚠️ | `QuoteItem` fields not fully verified against Krayin |
| Lead-product sync on quote item update | ❌ | Items and lead_products drift apart |
| View permission scoping | ⚠️ | Applied in `findAll`; verify `search` |
| Search quotes | ❌ | No search endpoint |
| Expired filter | ❌ | No filter |

---

## 8. Products & Inventory

| Feature | Status | Notes |
|---|---|---|
| List products | ✅ | `GET /api/products` |
| Create / edit / delete product | ✅ | Standard CRUD |
| Product search | ✅ | `GET /api/products/search` |
| Inventory per warehouse | ✅ | `POST /api/products/{id}/inventories/{warehouseId}` |
| Tags on products | ✅ | `POST/DELETE /api/products/{id}/tags/{tagId}` |
| Activities on products | ⚠️ | Via `?productId=` filter on activities |
| `products.create` permission | ❌ | Uses `products.edit` |
| View permission scoping | ❌ | Not applied (products are tenant-level, not user-scoped — verify intent) |
| Image / file uploads | ❌ | Not supported |

---

## 9. Warehouses

| Feature | Status | Notes |
|---|---|---|
| List warehouses | ✅ | `GET /api/warehouses` |
| CRUD warehouse | ✅ | Standard |
| Warehouse locations | ✅ | `WarehouseLocation` entity with `warehouseId` |
| Tags | ✅ | `POST/DELETE /api/warehouses/{id}/tags/{tagId}` |
| Activities | ✅ | Activity entity has `warehouseId` |
| Search | ⚠️ | Not exposed as endpoint |
| `warehouses.create` permission | ❌ | Uses `warehouses.edit` |

---

## 10. Pipelines & Stages

| Feature | Status | Notes |
|---|---|---|
| List pipelines | ✅ | `GET /api/pipelines` |
| CRUD pipeline | ✅ | Standard |
| CRUD stages | ✅ | Nested under pipeline |
| Reorder stages | ✅ | Tested in `PipelineReorderIntegrationTest` |
| `isDefault` flag | ✅ | One pipeline marked default per tenant |
| `rotten_days` | ❌ | Missing column (P1-5) |
| Delete-pipeline guard (default) | ❌ | Service allows it (P1-5) |
| Delete-pipeline-migrates-leads | ❌ | Leads orphaned on delete (P1-5) |
| Stage `code` (won/lost) | ✅ | Used in `LeadService.moveStage` |
| Stage probability | ✅ | Column exists |

---

## 11. Tags

| Feature | Status | Notes |
|---|---|---|
| List tags | ✅ | `GET /api/tags` |
| CRUD tag | ✅ | Standard |
| Color | ✅ | Column exists |
| Search | ✅ | `GET /api/tags/search` |
| Per-tenant uniqueness on name | ❌ | Globally unique (P0-2) |

---

## 12. Attributes (Custom fields)

| Feature | Status | Notes |
|---|---|---|
| Attribute CRUD | ✅ | `Attribute`, `AttributeOption`, `AttributeValue` entities exist |
| EAV value storage | ✅ | `AttributeValue` has `entityType`, `entityId`, `value` |
| Per-entity attribute mapping | ⚠️ | UI present but service-side validation/lookup endpoints likely incomplete |
| Attribute type enum | ✅ | `AttributeType` enum |
| Lookup attributes | ⚠️ | Krayin has `attributes.lookup/lookup-entity`; not verified |
| Quick-add attribute | ❌ | No `.quick-create` semantic |
| Mass update / delete | ⚠️ | Verify in controller |
| CSV download of attribute config | ❌ | Krayin has `attributes.download` |

---

## 13. Marketing — Events & Campaigns

| Feature | Status | Notes |
|---|---|---|
| Event CRUD | ✅ | `MarketingEvent` entity + endpoints |
| Campaign CRUD | ✅ | `MarketingCampaign` entity + endpoints |
| Campaign references event + email template | ✅ | FK columns present |
| Campaign send/schedule | ❌ | No execution engine yet |
| Per-tenant uniqueness on `name` | ❌ | Globally unique (P0-2) |
| Delete event with linked campaign guard | ❌ | (P1.9) |

---

## 14. Automation — Workflows & Webhooks

| Feature | Status | Notes |
|---|---|---|
| Workflow CRUD | ✅ | Entity + endpoints |
| Webhook CRUD | ✅ | Entity + endpoints |
| Trigger entities | ⚠️ | Workflow `eventName` field exists; Krayin supports leads/persons/activities/quotes |
| Conditions (JSON) | ⚠️ | Stored; evaluator implementation status unclear |
| Action types | ❌ | Only `LOG` implemented (P3 work) |
| Webhook execution | ⚠️ | Status unclear; needs verification |
| Workflow runs / observability | ❌ | No `workflow_action_runs` table yet |

---

## 15. Web Forms

| Feature | Status | Notes |
|---|---|---|
| WebForm CRUD | ✅ | Entity + endpoints |
| Public form retrieval | ✅ | `GET /web-forms/*` permitted in `SecurityConfig` |
| Public submission endpoint | ❌ | `POST /web-forms/*/submit` is allow-listed but service may not be implemented (P2.6 / `02 § 2.6`) |
| Lead/person creation from submission | ❌ | Requires service logic |
| Rate limiting | ❌ | Krayin throttles 100/60s; we don't |
| Attribute-based field config | ⚠️ | `WebFormAttribute` exists; rendering and validation status not verified |

---

## 16. Data Imports

| Feature | Status | Notes |
|---|---|---|
| Import CRUD | ✅ | `DataImport` entity + endpoints |
| Status tracking (success/error counts) | ✅ | Columns exist |
| CSV parsing | ⚠️ | `commons-csv` dep is included; verify pipeline |
| Excel parsing | ❌ | Krayin supports; we don't |
| Validate / start / link / index / stats endpoints | ⚠️ | Krayin has all of them; ours likely simpler |
| Error report download | ⚠️ | Verify |
| Sample download | ⚠️ | Verify |

---

## 17. Email Templates

| Feature | Status | Notes |
|---|---|---|
| Template CRUD | ✅ | Entity + endpoints |
| Predefined templates | ✅ | `isPredefined` column |
| Used by Quote send-mail | ✅ | Verified through `QuoteController.sendMail` |
| Used by workflow actions | ❌ | Pending workflow action engine (P3.2) |

---

## 18. DataGrid (UI saved filters)

| Feature | Status | Notes |
|---|---|---|
| Saved filter CRUD | ✅ | `DataGridSavedFilter` entity + endpoints |
| Per-user filter | ✅ | Tied to `userId` |
| Per-grid filter | ✅ | Grid identifier column |

---

## 19. Dashboard

| Feature | Status | Notes |
|---|---|---|
| Basic stats | ✅ | `DashboardController` exists |
| Full Krayin parity (`over-all`, `revenue-stats`, etc.) | ❌ | Eight stat types to add (P3.1) |
| Time-bucketed series | ❌ | Pending |

---

## 20. Multi-tenancy — _features Krayin doesn't have_

| Feature | Status | Notes |
|---|---|---|
| `tenants` table | ✅ | But shallow (P0-3) |
| `tenant_id` on all domain tables | ✅ | Since `V019` |
| Hibernate `@Filter(tenantFilter)` | ✅ | Declared everywhere |
| Tenant context per request | ❌ | **Broken — see P0-1**; every request runs as seed tenant |
| `Tenant` JPA entity | ❌ | Missing (P0-6) |
| Tenant provisioning service | ❌ | Missing (P0-3) |
| Per-tenant role / pipeline / source seeding | ❌ | Missing (P0-3) |
| Postgres RLS | ❌ | Recommended Phase 0 / 2 (P2-2) |

---

## 21. Cross-company sharing — _our unique differentiator_

| Feature | Status | Notes |
|---|---|---|
| `tenant_relationships` | ❌ | Phase 2 |
| `tenant_share_policies` | ❌ | Phase 2 |
| `record_shares` | ❌ | Phase 2 |
| `resource_visibility` index | ❌ | Phase 2 |
| Cross-tenant audit log | ❌ | Phase 2 |
| Share-aware RLS / Hibernate filter | ❌ | Phase 2 |
| Notification of incoming shares | ❌ | Phase 2 |

Full design in `03-cross-company-sharing.md`.
