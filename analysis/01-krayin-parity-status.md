# Krayin CRM Parity Status

Legend: ✅ Implemented | ⚠️ Partial | ❌ Missing

---

## 1. Authentication

| Feature | Status | Notes |
|---------|--------|-------|
| Login with email/password | ✅ | JWT (`POST /auth/login`) |
| Logout | ✅ | `POST /auth/logout` |
| Remember me | ❌ | JWT is stateless; add refresh token TTL config |
| Forgot password (email token) | ✅ | `POST /auth/forgot-password` |
| Reset password via token | ✅ | `POST /auth/reset-password` |
| Account edit (name, email, password, profile image) | ⚠️ | `PUT /auth/account` exists; profile image upload needs verification |
| Block inactive users on login | ✅ | `isActive` check in AuthService |

---

## 2. Leads

| Feature | Status | Notes |
|---------|--------|-------|
| List leads (paginated) | ✅ | `GET /leads` |
| Kanban view grouped by stage | ✅ | `GET /leads/kanban` |
| Create lead | ✅ | `POST /leads` |
| Create lead with inline person | ❌ | Must create person separately first |
| AI lead creation from file upload | ❌ | Krayin has LLM extraction; not planned for now |
| View lead detail | ✅ | `GET /leads/{id}` |
| Update lead | ✅ | `PUT /leads/{id}` |
| Move lead stage | ✅ | `PATCH /leads/{id}/stage` |
| Auto-set `closedAt` on won/lost stage | ⚠️ | Logic exists in moveStage; needs test coverage |
| Search leads (autocomplete) | ✅ | `GET /leads/search` |
| Delete lead | ✅ | `DELETE /leads/{id}` |
| Mass update leads (stage) | ✅ | `POST /leads/mass-update` |
| Mass delete leads | ✅ | `POST /leads/mass-destroy` |
| Attach/detach tags | ✅ | `POST/DELETE /leads/{id}/tags/{tagId}` |
| Attach/detach products | ✅ | `POST/DELETE /leads/{id}/products/{productId}` |
| List emails for lead | ✅ | `GET /leads/{id}/emails` |
| Attach/detach email | ✅ | `POST/DELETE /leads/{id}/emails/{emailId}` |
| Kanban look-up (filter dropdowns) | ❌ | No dedicated lookup endpoint |
| Rotten leads (rotten_days threshold) | ❌ | `rottenDays` not on Pipeline; not computed |
| View permission scoping (global/group/individual) | ⚠️ | `resolveScope()` in LeadService; group-scoping needs verification |

---

## 3. Contacts — Persons

| Feature | Status | Notes |
|---------|--------|-------|
| List persons (paginated) | ✅ | `GET /contacts/persons` |
| Create person | ✅ | `POST /contacts/persons` |
| View person detail | ✅ | `GET /contacts/persons/{id}` |
| Edit person | ✅ | `PUT /contacts/persons/{id}` |
| Search persons (autocomplete) | ✅ | `GET /contacts/persons/search` |
| Delete person | ✅ | `DELETE /contacts/persons/{id}` |
| Block delete if person has leads | ❌ | No guard; foreign key may cascade |
| Mass delete persons | ✅ | `POST /contacts/persons/mass-destroy` |
| Partial-success mass delete (skip if has leads) | ❌ | All-or-nothing |
| Attach/detach tags | ✅ | `POST/DELETE /contacts/persons/{id}/tags/{tagId}` |
| Activities for person | ⚠️ | Filter via `GET /activities?personId={id}` (no dedicated endpoint) |
| Multiple emails (JSON array) | ⚠️ | Domain stores single `email` string, not JSON array like Krayin |
| Multiple contact numbers (JSON array) | ⚠️ | Domain stores single `phone` string, not JSON array |
| Job title | ✅ | `jobTitle` on Person entity |
| View permission scoping | ⚠️ | Likely not applied to persons yet |

---

## 4. Contacts — Organizations

| Feature | Status | Notes |
|---------|--------|-------|
| List organizations | ✅ | `GET /contacts/organizations` |
| Create organization | ✅ | `POST /contacts/organizations` |
| Edit organization | ✅ | `PUT /contacts/organizations/{id}` |
| Delete organization | ✅ | `DELETE /contacts/organizations/{id}` |
| Mass delete | ✅ | `POST /contacts/organizations/mass-destroy` |
| Person count per org (DataGrid) | ❌ | Not computed in listing |
| Address (JSON) | ⚠️ | Needs verification that address is stored as structured JSON |
| Search organizations | ⚠️ | Not explicitly listed; may only be in contact lookup |

---

## 5. Activities

| Feature | Status | Notes |
|---------|--------|-------|
| List activities (paginated) | ✅ | `GET /activities` |
| Filter by entity (lead/person/product/warehouse) | ✅ | Query params |
| Create activity | ✅ | `POST /activities` |
| Edit activity | ✅ | `PUT /activities/{id}` |
| Toggle done/not-done | ✅ | `PATCH /activities/{id}/done` |
| Mass mark done/undone | ✅ | `POST /activities/mass-update` |
| Mass delete | ✅ | `POST /activities/mass-destroy` |
| Delete activity | ✅ | `DELETE /activities/{id}` |
| Activity types (call, meeting, lunch, note, file) | ⚠️ | `ActivityType` enum exists but lists TASK, not krayin types; needs update |
| Auto-done for notes | ❌ | No special handling for `note` type |
| Location field | ❌ | Not on Activity entity |
| Additional JSON field | ❌ | Not on Activity entity |
| Calendar view (date range query) | ❌ | No calendar-specific endpoint |
| User participants | ✅ | `activity_participants` table, `POST/DELETE /activities/{id}/participants` |
| Person participants | ❌ | Participants are user-only; Krayin supports person participants too |
| Meeting overlap check | ❌ | No validation for overlapping meetings |
| File upload to activity | ✅ | `POST /activities/{id}/file` |
| File download | ✅ | `GET /activities/{id}/file/{fileId}/download` |

---

## 6. Mail / Email

| Feature | Status | Notes |
|---------|--------|-------|
| List emails by folder (inbox/draft/outbox/sent/trash) | ✅ | `GET /mail?folder=inbox` |
| View email thread | ⚠️ | `GET /mail/{id}` exists; thread children loading not verified |
| Compose and send email | ✅ | `POST /mail` |
| Save as draft | ❌ | Compose always sends; no draft mode |
| Edit and send draft | ❌ | No draft send endpoint |
| Reply to email (parent_id) | ⚠️ | `parentId` field in compose request; thread structure exists |
| Forward email | ❌ | No forward endpoint |
| Move email to folder | ✅ | `PATCH /mail/{id}/folder` |
| Mark as read/unread | ✅ | `PATCH /mail/{id}/read` |
| Delete email (trash / permanent) | ✅ | `DELETE /mail/{id}` |
| Mass move to folder | ✅ | `POST /mail/mass-update` |
| Mass delete | ✅ | `POST /mail/mass-destroy` |
| Attach/detach tags | ✅ | `POST/DELETE /mail/{id}/tags/{tagId}` |
| Download attachment | ✅ | `GET /mail/attachments/{attachmentId}/download` |
| Inbound parse webhook | ✅ | `POST /mail/inbound-parse` (no auth required) |
| Per-folder permissions (mail.inbox, mail.draft, etc.) | ❌ | Single `mail.view` permission; Krayin has per-folder |
| Attachment upload on compose | ⚠️ | No multipart/form-data support on compose endpoint |

---

## 7. Quotes

| Feature | Status | Notes |
|---------|--------|-------|
| List quotes | ✅ | `GET /quotes` |
| Create quote | ✅ | `POST /quotes` |
| Edit quote | ✅ | `PUT /quotes/{id}` |
| Delete quote | ✅ | `DELETE /quotes/{id}` |
| Mass delete | ✅ | `POST /quotes/mass-destroy` |
| Print / download PDF | ✅ | `GET /quotes/{id}/print` |
| Send quote by email | ✅ | `POST /quotes/{id}/send-mail` |
| Get lead products as quote items | ✅ | `GET /quotes/lead-products/{leadId}` |
| Duplicate quote | ✅ | `POST /quotes/{id}/duplicate` (beyond Krayin) |
| Billing / shipping address | ⚠️ | Not on Quote domain entity; needs addition |
| Quote item: discount, tax per line | ⚠️ | Needs verification on QuoteItem entity |
| Lead-product sync on quote item update | ❌ | Quote item changes should sync back to lead_products |
| View permission scoping | ⚠️ | Not verified |

