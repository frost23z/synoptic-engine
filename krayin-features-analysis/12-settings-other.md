# Settings — Email Templates, Marketing, Web Forms, Sources, Types, Tags, Imports, Config

---

## Email Templates

### User Stories
- As a user I can list all email templates.
- As a user I can create an email template with a name, subject, and HTML body.
- As a user I can edit an email template.
- As a user I can delete an email template.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/email-templates` | List (DataGrid) |
| GET | `GET /settings/email-templates/create` | Create form |
| POST | `POST /settings/email-templates/create` | Create |
| GET | `GET /settings/email-templates/edit/{id?}` | Edit form |
| PUT | `PUT /settings/email-templates/edit/{id}` | Update |
| DELETE | `DELETE /settings/email-templates/{id}` | Delete |

### Request Body
```
name     string  required  unique
subject  string  required
content  text    required  HTML body
```

### DB Schema: `email_templates`

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | unique |
| subject | varchar | |
| content | text | HTML |
| created_at | timestamp | |
| updated_at | timestamp | |

### Permissions
`settings.automation.email_templates` (+ `.create`, `.edit`, `.delete`)

---

## Marketing Events

### User Stories
- As a user I can list all marketing events.
- As a user I can create a marketing event with name, description, and date.
- As a user I can edit a marketing event.
- As a user I can delete a marketing event (blocked if campaigns reference it).
- As a user I can mass-delete marketing events (skips those with campaigns).

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/marketing/events` | List (DataGrid) |
| POST | `POST /settings/marketing/events/create` | Create |
| GET | `GET /settings/marketing/events/edit/{id?}` | Edit form |
| PUT | `PUT /settings/marketing/events/edit/{id}` | Update |
| DELETE | `DELETE /settings/marketing/events/{id}` | Delete |
| POST | `POST /settings/marketing/events/mass-destroy` | Mass delete |

### Request Body
```
name         string  required  max 60
description  string  required
date         date    required  must be today or in the future (after_or_equal:today)
```

### DB Schema: `marketing_events`

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | |
| description | varchar | |
| date | date | |
| created_at | timestamp | |
| updated_at | timestamp | |

---

## Marketing Campaigns

### User Stories
- As a user I can list all marketing campaigns.
- As a user I can create a campaign linking an email template and a marketing event.
- As a user I can enable/disable a campaign.
- As a user I can edit a campaign.
- As a user I can delete a campaign.
- As a user I can mass-delete campaigns.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/marketing/campaigns` | List (DataGrid) |
| GET | `GET /settings/marketing/campaigns/events` | Get available events (JSON) |
| GET | `GET /settings/marketing/campaigns/email-templates` | Get available templates (JSON) |
| POST | `POST /settings/marketing/campaigns` | Create |
| GET | `GET /settings/marketing/campaigns/{id}` | Show (JSON) |
| PUT | `PUT /settings/marketing/campaigns/{id}` | Update |
| DELETE | `DELETE /settings/marketing/campaigns/{id}` | Delete |
| POST | `POST /settings/marketing/campaigns/mass-destroy` | Mass delete |

### Request Body
```
name                   string   required  max 255
subject                string   required  max 255
marketing_template_id  int      required  exists:email_templates
marketing_event_id     int      required  exists:marketing_events
status                 boolean  optional  in: 0, 1
```

### DB Schema: `marketing_campaigns`

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | |
| subject | varchar | |
| status | tinyint | 0 = inactive, 1 = active |
| type | varchar | campaign type |
| mail_to | varchar | recipient type |
| spooling | varchar | nullable |
| marketing_template_id | int unsigned | FK → email_templates ON DELETE SET NULL |
| marketing_event_id | int unsigned | FK → marketing_events ON DELETE SET NULL |
| created_at | timestamp | |
| updated_at | timestamp | |

### Permissions
`settings.automation.events` / `settings.automation.campaigns` (+ `.create`, `.edit`, `.delete`)

---

## Web Forms

### User Stories
- As a user I can list all web forms.
- As a user I can create a web form that maps person and lead attributes to form fields.
- As a user I can configure web form appearance (colors).
- As a user I can set whether the form creates a lead on submission.
- As a user I can configure the success action (message or redirect URL).
- As a user I can edit and delete web forms.
- External visitors can submit a web form via a public URL (using the `form_id`).

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/web-forms` | List (DataGrid) |
| GET | `GET /settings/web-forms/create` | Create form |
| POST | `POST /settings/web-forms/create` | Create |
| GET | `GET /settings/web-forms/edit/{id?}` | Edit form |
| PUT | `PUT /settings/web-forms/edit/{id}` | Update |
| DELETE | `DELETE /settings/web-forms/{id}` | Delete |

### Request Body
```
title                       string   required
description                 text     optional
submit_button_label         text     required
submit_success_action       string   required  e.g. 'message' or 'redirect'
submit_success_content      string   required  message text or redirect URL
create_lead                 boolean  optional  default 0
background_color            string   optional  hex color
form_background_color       string   optional
form_title_color            string   optional
form_submit_button_color    string   optional
attribute_label_color       string   optional
attributes                  array    optional  attribute configurations
```

### DB Schema

**`web_forms`**

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| form_id | varchar | unique public identifier |
| title | varchar | |
| description | text | |
| submit_button_label | text | |
| submit_success_action | varchar | 'message' or 'redirect' |
| submit_success_content | varchar | |
| create_lead | tinyint | 0 or 1 |
| background_color | varchar | |
| form_background_color | varchar | |
| form_title_color | varchar | |
| form_submit_button_color | varchar | |
| attribute_label_color | varchar | |
| created_at | timestamp | |
| updated_at | timestamp | |

**`web_form_attributes`**

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | label override |
| placeholder | varchar | |
| is_required | tinyint | |
| is_hidden | tinyint | |
| sort_order | int | |
| attribute_id | int unsigned | FK → attributes ON DELETE CASCADE |
| web_form_id | int unsigned | FK → web_forms ON DELETE CASCADE |

The form includes attributes from `entity_type` in (`persons`, `leads`). Default attributes (`name`, `emails`, `contact_numbers`) are shown first.

---

## Lead Sources and Types

### Sources

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/sources` | List |
| POST | `POST /settings/sources/create` | Create |
| GET | `GET /settings/sources/edit/{id?}` | Edit form |
| PUT | `PUT /settings/sources/edit/{id}` | Update |
| DELETE | `DELETE /settings/sources/{id}` | Delete |

Request: `{ name: string required }`

**DB: `lead_sources`** — `id`, `name`, timestamps.

**Permissions:** `settings.lead.sources` (+ `.create`, `.edit`, `.delete`)

### Types

Same structure as Sources.

**DB: `lead_types`** — `id`, `name`, timestamps.

**Permissions:** `settings.lead.types` (+ `.create`, `.edit`, `.delete`)

---

## Tags (Settings)

### User Stories
- As a user I can list all tags.
- As a user I can create, edit, delete tags.
- As a user I can search tags by keyword.
- As a user I can mass-delete tags.
- Tags can be applied to: leads, persons, emails, products, warehouses.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/tags` | List (DataGrid) |
| POST | `POST /settings/tags/create` | Create |
| GET | `GET /settings/tags/edit/{id}` | Edit form |
| PUT | `PUT /settings/tags/edit/{id}` | Update |
| GET | `GET /settings/tags/search` | Search tags |
| DELETE | `DELETE /settings/tags/{id}` | Delete |
| POST | `POST /settings/tags/mass-destroy` | Mass delete |

### Request Body
```
name   string  required
color  string  optional  hex color code
```

### DB Schema: `tags`

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | |
| color | varchar | hex color e.g. '#FF5733' |
| user_id | int unsigned | FK → users ON DELETE CASCADE (creator) |
| created_at | timestamp | |
| updated_at | timestamp | |

### Permissions
`settings.other_settings.tags` (+ `.create`, `.edit`, `.delete`)

---

## Warehouses

### User Stories
- As a user I can list all warehouses.
- As a user I can create a warehouse with name, description, contact info, and address.
- As a user I can view a warehouse's detail page.
- As a user I can edit a warehouse.
- As a user I can delete a warehouse.
- As a user I can search warehouses.
- As a user I can view products stocked in a warehouse.
- As a user I can manage locations within a warehouse.
- As a user I can attach/detach tags on a warehouse.
- As a user I can view activities for a warehouse.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/warehouses` | List (DataGrid) |
| GET | `GET /settings/warehouses/search` | Search |
| GET | `GET /settings/warehouses/{id}/products` | Products in warehouse |
| GET | `GET /settings/warehouses/create` | Create form |
| POST | `POST /settings/warehouses/create` | Create |
| GET | `GET /settings/warehouses/view/{id}` | View detail |
| GET | `GET /settings/warehouses/edit/{id?}` | Edit form |
| PUT | `PUT /settings/warehouses/edit/{id}` | Update |
| DELETE | `DELETE /settings/warehouses/{id}` | Delete |

**Warehouse Tags:**
- `POST /settings/warehouses/{id}/tags`
- `DELETE /settings/warehouses/{id}/tags`

**Warehouse Activities:**
- `GET /settings/warehouses/{id}/activities`

**Warehouse Locations:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/locations/search` | Search locations |
| POST | `POST /settings/locations/create` | Create location |
| PUT | `PUT /settings/locations/edit/{id}` | Update location |
| DELETE | `DELETE /settings/locations/{id}` | Delete location |

### DB Schema

**`warehouses`**

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| name | varchar | no | |
| description | text | yes | |
| contact_name | varchar | no | |
| contact_emails | json | no | array |
| contact_numbers | json | no | array |
| contact_address | json | no | |
| created_at | timestamp | | |
| updated_at | timestamp | | |

**`warehouse_locations`**

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| name | varchar | unique per warehouse |
| warehouse_id | int unsigned | FK → warehouses ON DELETE CASCADE |
| created_at | timestamp | |
| updated_at | timestamp | |

Unique constraint: `(warehouse_id, name)`.

**`warehouse_tags`**

| Column | Notes |
|--------|-------|
| tag_id | FK → tags |
| warehouse_id | FK → warehouses |

**`warehouse_activities`**

| Column | Notes |
|--------|-------|
| activity_id | FK → activities |
| warehouse_id | FK → warehouses |

### Permissions
`settings.inventory.warehouse` (+ `.create`, `.edit`, `.delete`)

---

## Data Import (DataTransfer)

### User Stories
- As a user I can list all import jobs.
- As a user I can create a new import job by uploading a CSV/Excel file.
- As a user I can edit an import job.
- As a user I can validate an import to preview errors before processing.
- As a user I can start processing an import in batches.
- As a user I can link imported records (resolve relationships).
- As a user I can index imported data (for search).
- As a user I can check the stats of an import in progress.
- As a user I can delete an import job.
- As a user I can download the original import file.
- As a user I can download the error report file.
- As a user I can download a sample CSV for a given importer type.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /settings/data-transfer/imports` | List (DataGrid) |
| GET | `GET /settings/data-transfer/imports/create` | Create form |
| POST | `POST /settings/data-transfer/imports/create` | Create import |
| GET | `GET /settings/data-transfer/imports/edit/{id}` | Edit form |
| PUT | `PUT /settings/data-transfer/imports/update/{id}` | Update import |
| DELETE | `DELETE /settings/data-transfer/imports/destroy/{id}` | Delete |
| GET | `GET /settings/data-transfer/imports/import/{id}` | Show import progress page |
| GET | `GET /settings/data-transfer/imports/validate/{id}` | Validate (JSON) |
| GET | `GET /settings/data-transfer/imports/start/{id}` | Start batch processing (JSON) |
| GET | `GET /settings/data-transfer/imports/link/{id}` | Link records (JSON) |
| GET | `GET /settings/data-transfer/imports/index/{id}` | Index data (JSON) |
| GET | `GET /settings/data-transfer/imports/stats/{id}/{state?}` | Get stats (JSON) |
| GET | `GET /settings/data-transfer/imports/download-sample/{sample?}` | Download sample |
| GET | `GET /settings/data-transfer/imports/download/{id}` | Download import file |
| GET | `GET /settings/data-transfer/imports/download-error-report/{id}` | Download error report |

### Request Body (Create)
```
type                 string   required  in: [configured importers]
action               string   required  in: append | delete
validation_strategy  string   required  in: stop-on-errors | skip-errors
allowed_errors       int      required  min 0
field_separator      string   required  e.g. ','
process_in_queue     boolean  optional
file                 file     required  mimes: csv, xls, xlsx, txt
```

### Import States

| State | Description |
|-------|-------------|
| `pending` | Created, not yet processed |
| `validated` | Validation passed |
| `processing` | Batch import running |
| `processed` | All batches processed |
| `linking` | Resolving cross-record relationships |
| `linked` | Linking complete |
| `indexing` | Building search indexes |
| `indexed` | Indexing complete |
| `completed` | Full pipeline done |

### DB Schema

**`imports`**

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| state | varchar | default 'pending' |
| process_in_queue | tinyint | 0 or 1 |
| type | varchar | importer type |
| action | varchar | append / delete |
| validation_strategy | varchar | stop-on-errors / skip-errors |
| allowed_errors | int | max allowed errors |
| processed_rows_count | int | |
| invalid_rows_count | int | |
| errors_count | int | |
| errors | json | |
| field_separator | varchar | |
| file_path | varchar | uploaded file path |
| error_file_path | varchar | |
| summary | json | |
| started_at | datetime | |
| completed_at | datetime | |
| created_at | timestamp | |
| updated_at | timestamp | |

**`import_batches`**

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| state | varchar | default 'pending' |
| data | json | batch row data |
| summary | json | |
| import_id | int unsigned | FK → imports ON DELETE CASCADE |

### Permissions
`settings.automation.data_transfer.imports` (+ `.create`, `.edit`, `.delete`, `.import`)

---

## System Configuration

### Overview
Krayin has a `core_config` table storing key-value pairs for system settings. The configuration is browsable/editable through the admin Configuration section.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `GET /configuration` | Configuration index |
| GET | `GET /configuration/{slug}/{slug2}` | Specific config section form |
| POST | `POST /configuration/store` | Save config values |
| GET | `GET /configuration/download/{path}` | Download config file value |
| GET | `GET /configuration/search` | Search config keys |

### DB Schema: `core_config`

| Column | Type | Notes |
|--------|------|-------|
| id | int unsigned | PK |
| code | varchar | dot-notation key |
| value | text | stored value (updated 2025: column type changed to TEXT) |
| created_at | timestamp | |
| updated_at | timestamp | |

### Config Sections (from `core_config.php`)

**General group:**
- `general.general.locale_settings` — locale (select)
- `general.general.admin_logo` — logo image, favicon
- `general.settings.footer` — footer HTML (editor)
- `general.settings.menu` — menu item labels (text, max 20 chars each)
- `general.settings.menu_color` — brand color (color picker, default #0E90D9)
- `general.magic_ai.settings` — AI enable, API key, model selection
- `general.magic_ai.doc_generation` — doc generation enable

**Email group:**
- `email.imap.account` — IMAP host, port, encryption, validate_cert, username, password

### Permissions
`configuration`
