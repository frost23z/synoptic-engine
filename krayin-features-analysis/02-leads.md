# Leads

## User Stories

- As a user I can view a paginated list of leads in a table (DataGrid view).
- As a user I can view leads in a Kanban board organized by pipeline stages.
- As a user I can create a new lead with a contact person, value, type, source, and pipeline stage.
- As a user I can create a lead "by AI" by uploading PDF/image files; the system extracts lead data via LLM.
- As a user I can view the full detail page of a lead.
- As a user I can edit a lead's core fields.
- As a user I can update a lead's custom attributes separately (without re-submitting all fields).
- As a user I can move a lead to a different pipeline stage, optionally recording a close date or lost reason.
- As a user I can search for leads by keyword.
- As a user I can mass-update (change stage for) multiple leads at once.
- As a user I can mass-delete multiple leads.
- As a user I can attach/detach tags on a lead.
- As a user I can attach/detach products to a lead with quantity and price.
- As a user I can view activities associated with a lead.
- As a user I can compose and send emails linked to a lead.
- As a user I can detach an email from a lead.
- As a user I can create a quote from a lead (pre-filled with lead's products).
- As a user I can mail a quote that is linked to a lead.
- As a user I can delete a quote linked to a lead.
- As a user I can look up Kanban column values (for filter dropdowns).

## API Endpoints

### Lead CRUD

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.leads.index | `GET /leads` | List (DataGrid JSON when AJAX, Kanban HTML otherwise) |
| GET | admin.leads.get | `GET /leads/get` | Kanban JSON data grouped by stage |
| GET | admin.leads.create | `GET /leads/create` | Create form |
| POST | admin.leads.store | `POST /leads/create` | Create lead |
| POST | admin.leads.create_by_ai | `POST /leads/create-by-ai` | Create lead(s) from uploaded files via LLM |
| GET | admin.leads.view | `GET /leads/view/{id}` | Lead detail page |
| GET | admin.leads.edit | `GET /leads/edit/{id}` | Edit form |
| PUT | admin.leads.update | `PUT /leads/edit/{id}` | Full update |
| PUT | admin.leads.attributes.update | `PUT /leads/attributes/edit/{id}` | Partial attribute update |
| PUT | admin.leads.stage.update | `PUT /leads/stage/edit/{id}` | Stage-only update |
| GET | admin.leads.search | `GET /leads/search` | Autocomplete search |
| DELETE | admin.leads.delete | `DELETE /leads/{id}` | Delete one |
| POST | admin.leads.mass_update | `POST /leads/mass-update` | Mass change stage |
| POST | admin.leads.mass_delete | `POST /leads/mass-destroy` | Mass delete |
| GET | admin.leads.kanban.look_up | `GET /leads/kanban/look-up` | Kanban filter lookup |

### Lead Products

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| PUT | admin.leads.product.add | `PUT /leads/product/{leadId}` | Attach/update product on lead |
| DELETE | admin.leads.product.remove | `DELETE /leads/product/{leadId}` | Remove product from lead |

### Lead Tags

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| POST | admin.leads.tags.attach | `POST /leads/{id}/tags` | Attach tag |
| DELETE | admin.leads.tags.detach | `DELETE /leads/{id}/tags` | Detach tag |

### Lead Emails

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| POST | admin.leads.emails.store | `POST /leads/{id}/emails` | Link email to lead |
| DELETE | admin.leads.emails.detach | `DELETE /leads/{id}/emails` | Unlink email from lead |

### Lead Activities

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.leads.activities.index | `GET /leads/{id}/activities` | List activities for lead |

### Lead Quotes

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| POST | admin.leads.quotes.mail | `POST /leads/quotes/{quoteId}/mail` | Email a quote |
| DELETE | admin.leads.quotes.delete | `DELETE /leads/quotes/{quoteId?}` | Remove quote from lead |

---

### Request Bodies

**POST /leads/create (LeadForm validation):**
```
title                   string    required
description             text      optional
lead_value              decimal   optional
lead_type_id            int       optional, FK → lead_types
lead_source_id          int       optional, FK → lead_sources
expected_close_date     date      optional
user_id                 int       optional, FK → users
lead_pipeline_id        int       optional (defaults to default pipeline)
lead_pipeline_stage_id  int       optional (defaults to first stage of pipeline)
status                  int       forced to 1 on create
person                  object    optional, person data (id or create)
  person.id             int       if existing person
  person.name           string    if creating new person
  person.emails         json      if creating new person
  person.contact_numbers json     if creating new person
  person.organization_id int      if creating new person
products                array     optional
  products[].product_id int
  products[].quantity   int
  products[].price      decimal
```

**PUT /leads/stage/edit/{id}:**
```
lead_pipeline_stage_id  int  required
closed_at               datetime  optional (set when stage is won/lost)
lost_reason             text      optional
```

**PUT /leads/product/{leadId}:**
```
product_id  int      required
quantity    int      required
price       decimal  required
```
`amount` is computed as `price * quantity`.

**POST /leads/mass-update:**
```
indices  int[]  required  lead IDs
value    int    required  stage ID to move leads to
```

**GET /leads/get (Kanban JSON):**
Query params: `pipeline_id` (optional), `pipeline_stage_id` (optional), any `RequestCriteria` search params.

Response: keyed by `sort_order`, each entry:
```json
{
  "id": 1,
  "name": "Stage Name",
  "sort_order": 1,
  "lead_value": 5000,
  "leads": {
    "data": [ ...LeadResource... ],
    "meta": { "current_page", "last_page", "per_page", "total", ... }
  }
}
```

---

## DB Schema

### `leads`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| title | varchar | no | | |
| description | text | yes | | |
| lead_value | decimal(12,4) | yes | | |
| status | tinyint | yes | | 1 = open |
| lost_reason | text | yes | | set when stage.code = 'lost' |
| closed_at | datetime | yes | | set when stage.code in (won, lost) |
| expected_close_date | date | yes | | |
| user_id | int unsigned | yes | | FK → users |
| person_id | int unsigned | yes | | FK → persons |
| lead_source_id | int unsigned | yes | | FK → lead_sources |
| lead_type_id | int unsigned | yes | | FK → lead_types |
| lead_pipeline_id | int unsigned | yes | | FK → lead_pipelines |
| lead_pipeline_stage_id | int unsigned | yes | | FK → lead_pipeline_stages |
| created_at | timestamp | yes | | |
| updated_at | timestamp | yes | | |

### `lead_sources`

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | |
| created_at | timestamp | |
| updated_at | timestamp | |

### `lead_types`

Same structure as `lead_sources`.

### `lead_products`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| quantity | int | no | default 0 |
| price | decimal(12,4) | yes | |
| amount | decimal(12,4) | yes | computed = price * quantity |
| lead_id | int unsigned | no | FK → leads ON DELETE CASCADE |
| product_id | int unsigned | no | FK → products ON DELETE CASCADE |
| created_at | timestamp | | |
| updated_at | timestamp | | |

### `lead_tags`

| Column | Type | Notes |
|--------|------|-------|
| lead_id | int unsigned | FK → leads |
| tag_id | int unsigned | FK → tags |

### `lead_activities`

| Column | Type | Notes |
|--------|------|-------|
| lead_id | int unsigned | FK → leads |
| activity_id | int unsigned | FK → activities |

### `lead_quotes`

| Column | Type | Notes |
|--------|------|-------|
| lead_id | int unsigned | FK → leads |
| quote_id | int unsigned | FK → quotes |

### `lead_pipelines`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| name | varchar | no | unique |
| is_default | tinyint | no | default 0 |
| rotten_days | int | yes | days before lead is "rotten" |
| created_at | timestamp | | |
| updated_at | timestamp | | |

### `lead_pipeline_stages`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| probability | int | no | default 0 |
| sort_order | int | no | default 0 |
| name | varchar | no | (from lead_stages) |
| code | varchar | no | special: `won`, `lost` are terminal |
| lead_stage_id | int unsigned | no | FK → lead_stages |
| lead_pipeline_id | int unsigned | no | FK → lead_pipelines ON DELETE CASCADE |

### `lead_stages` (global stage template)

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | |
| code | varchar | e.g. 'new', 'won', 'lost' |
| sort_order | int | |

---

## Filtering & Sorting (DataGrid)

The LeadDataGrid supports these columns, all filterable/sortable:

| Index | DB Column | Filter Type |
|-------|-----------|-------------|
| id | leads.id | integer |
| sales_person | users.name | searchable_dropdown (UserRepository) |
| title | leads.title | text search |
| lead_source_name | lead_sources.id | dropdown (all sources) |
| lead_value | leads.lead_value | text/range |
| lead_type_name | lead_types.id | dropdown (all types) |
| tag_name | tags.name | searchable_dropdown (TagRepository) |
| person_name | persons.name | searchable_dropdown (PersonRepository) |
| stage | lead_pipeline_stages.id | dropdown (stages of current pipeline) |
| rotten_lead | computed DATEDIFF | boolean |
| expected_close_date | leads.expected_close_date | date_range |
| created_at | leads.created_at | date_range |

**Rotten lead:** computed as `DATEDIFF(NOW(), created_at) >= lead_pipelines.rotten_days`. Won/lost leads are never rotten.

**View permission scoping:** `bouncer()->getAuthorizedUserIds()` filters `leads.user_id` IN authorized user IDs.

---

## Kanban Columns (filterable)

| Index | Filter Type |
|-------|------------|
| id | integer |
| lead_value | integer/string |
| user_id | searchable_dropdown (UserRepository) |
| person.id | searchable_dropdown (PersonRepository) |
| lead_type_id | dropdown (all types) |
| lead_source_id | dropdown (all sources) |
| tags.name | searchable_dropdown (TagRepository) |

---

## Business Rules

1. **Default pipeline:** On create, if no `lead_pipeline_id` given, uses `pipelineRepository->getDefaultPipeline()` (first pipeline where `is_default = 1`, or first pipeline if none).
2. **Stage assignment:** On create/update, if `lead_pipeline_stage_id` is given, `lead_pipeline_id` is derived from that stage's pipeline.
3. **closed_at auto-set:** When moving to a stage with `code = 'won'` or `code = 'lost'`, `closed_at` is set to `now()`. Moving back clears it.
4. **Person inline create:** If `data['person']` is provided without an `id`, a new person is created immediately and its id is used as `person_id`.
5. **Product amount:** `amount = price * quantity`, stored in `lead_products`.
6. **AI lead creation:** Accepts files with extensions `pdf, bmp, jpeg, jpg, png, webp`. Each file is base64-encoded and sent to `MagicAIService::extractDataFromFile`. The extracted data is mapped to lead fields. If a person with a matching email already exists, they are linked rather than duplicated.
7. **Events fired:** `lead.create.before`, `lead.create.after`, `lead.update.before`, `lead.update.after`, `lead.delete.before`, `lead.delete.after`, `lead.product.delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View list | `leads` |
| Create | `leads.create` |
| Quick-add | `leads.create.quick-create` |
| View detail | `leads.view` |
| Edit | `leads.edit` |
| Delete | `leads.delete` |
| Mass update | `leads.edit` |
| Mass delete | `leads.delete` |
