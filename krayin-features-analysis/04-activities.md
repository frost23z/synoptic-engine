# Activities

## User Stories

- As a user I can view all activities (calls, meetings, lunches) in a table or calendar view.
- As a user I can create an activity of type: call, meeting, lunch, note, or file.
- As a user I can schedule activities with a start/end date-time.
- As a user I can mark an activity as done or not done.
- As a user I can edit an existing activity.
- As a user I can link an activity to a lead.
- As a user I can add participants (users and/or persons) to an activity.
- As a user I can attach a file to an activity (type = 'file').
- As a user I can download an attached file from an activity.
- As a user I can delete an activity.
- As a user I can mass-update (mark done/undone) multiple activities.
- As a user I can mass-delete multiple activities.
- As a user I can view activities filtered to a date range for calendar/week view.
- As a user I can see activities associated with a specific lead.
- As a user I can see activities associated with a specific person.
- As a user I can see activities associated with a specific product.
- As a user I can see activities associated with a specific warehouse.

## Activity Types

| Code | Description |
|------|-------------|
| `call` | Phone call |
| `meeting` | Meeting (scheduled, can have participants, overlapping check) |
| `lunch` | Lunch meeting |
| `note` | Text note (no schedule required, auto-marked `is_done = 1`) |
| `file` | File attachment (no schedule required) |

---

## API Endpoints

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.activities.index | `GET /activities` | List page (HTML) |
| GET | admin.activities.get | `GET /activities/get` | DataGrid JSON or calendar JSON |
| POST | admin.activities.store | `POST /activities/create` | Create activity |
| GET | admin.activities.edit | `GET /activities/edit/{id}` | Edit form |
| PUT | admin.activities.update | `PUT /activities/edit/{id}` | Update activity |
| GET | admin.activities.file_download | `GET /activities/download/{id}` | Download file attachment |
| DELETE | admin.activities.delete | `DELETE /activities/{id}` | Delete |
| POST | admin.activities.mass_update | `POST /activities/mass-update` | Mass mark done/undone |
| POST | admin.activities.mass_delete | `POST /activities/mass-destroy` | Mass delete |

### Lead-scoped

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.leads.activities.index | `GET /leads/{id}/activities` | Activities for a lead |

### Person-scoped

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.contacts.persons.activities.index | `GET /contacts/persons/{id}/activities` | Activities for a person |

### Product-scoped

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.products.activities.index | `GET /products/{id}/activities` | Activities for a product |

### Warehouse-scoped

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.settings.warehouse.activities.index | `GET /settings/warehouses/{id}/activities` | Activities for a warehouse |

---

### GET /activities/get

**Without `view_type` param:** Returns DataGrid JSON.

**With `view_type` param:** Returns calendar JSON.
- Query param `startDate` (default: start of current week)
- Query param `endDate` (default: end of current week)
- Returns activities of type `call`, `meeting`, `lunch` (not notes/files) within the date range.

Response:
```json
{
  "activities": [
    {
      "id": 1,
      "title": "Demo call",
      "start": "2024-01-15 09:00:00",
      "end": "2024-01-15 10:00:00",
      "user_name": "John Doe",
      "class": "done",
      "created_at": "2024-01-14 12:00:00"
    }
  ]
}
```

---

### POST /activities/create

**Validation rules:**
```
type          string    required
comment       string    required_if:type,note
schedule_from datetime  required_unless:type,note,file
schedule_to   datetime  required_unless:type,note,file
file          file      required_if:type,file
```

**Additional for type = 'meeting':** Checks `isDurationOverlapping` against existing meetings' participants. Returns 400 if overlap detected.

**Request body:**
```
type            string    required  call|meeting|lunch|note|file
title           string    optional
comment         text      optional  (required if type = note)
location        string    optional
schedule_from   datetime  optional  (required unless type is note or file)
schedule_to     datetime  optional  (required unless type is note or file)
is_done         boolean   auto-set: true for notes, false for others
user_id         int       auto-set to current authenticated user
lead_id         int       optional  link to lead
participants.users[]   int[]  optional  user IDs
participants.persons[] int[]  optional  person IDs
file            file      optional  (required if type = file)
name            string    optional  filename override (for file type)
additional      json      optional
```

**File handling:** When `type = 'file'`, a `FileRepository::create` call stores the file to `activities/{activityId}/` path and records `name`, `path`, `activity_id` in `activity_files`.

**Participant handling:** On create, participants from `participants.users` and `participants.persons` are created in `activity_participants`. On update, all existing participants are deleted and recreated from the provided arrays.

---

### PUT /activities/edit/{id}

Same fields as create. Additionally:
- `lead_id`: if present (even empty string), syncs lead relationship. Empty string clears the lead association.
- Participants fully replaced on update.

### POST /activities/mass-update

```
indices  int[]  required  activity IDs
value    int    required  1 = done, 0 = not done
```

---

## DB Schema

### `activities`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| title | varchar | yes | | |
| type | varchar | no | | call/meeting/lunch/note/file |
| location | varchar | yes | | added 2021-11-17 |
| comment | text | yes | | |
| additional | json | yes | | arbitrary extra data |
| schedule_from | datetime | yes | | |
| schedule_to | datetime | yes | | |
| is_done | tinyint | no | 0 | |
| user_id | int unsigned | yes | | FK → users ON DELETE CASCADE (nullable since 2025-01-17) |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `activity_files`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| name | varchar | no | display name |
| path | varchar | no | storage path |
| activity_id | int unsigned | no | FK → activities ON DELETE CASCADE |
| created_at | timestamp | | |
| updated_at | timestamp | | |

### `activity_participants`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| activity_id | int unsigned | no | FK → activities ON DELETE CASCADE |
| user_id | int unsigned | yes | FK → users ON DELETE CASCADE |
| person_id | int unsigned | yes | FK → persons ON DELETE CASCADE |

One of `user_id` or `person_id` is set per row (not both).

### `lead_activities`

| Column | Type | Notes |
|--------|------|-------|
| lead_id | int unsigned | FK → leads |
| activity_id | int unsigned | FK → activities |

### `person_activities`

| Column | Type | Notes |
|--------|------|-------|
| person_id | int unsigned | FK → persons |
| activity_id | int unsigned | FK → activities |

### `product_activities`

| Column | Type | Notes |
|--------|------|-------|
| product_id | int unsigned | FK → products |
| activity_id | int unsigned | FK → activities |

### `warehouse_activities`

| Column | Type | Notes |
|--------|------|-------|
| warehouse_id | int unsigned | FK → warehouses |
| activity_id | int unsigned | FK → activities |

---

## Filtering & Sorting (DataGrid)

The ActivityDataGrid shows only types `call`, `meeting`, `lunch` (not notes/files).

| Index | DB Column | Filter Type |
|-------|-----------|-------------|
| id | activities.id | integer |
| is_done | activities.is_done | boolean dropdown (yes/no) |
| title | activities.title | text (searchable, sortable) |
| created_by_id | users.name | searchable_dropdown (UserRepository) |
| comment | activities.comment | text |
| lead_title | leads.title | searchable_dropdown (LeadRepository) |
| type | activities.type | not filterable |
| schedule_from | activities.schedule_from | date (sortable/filterable) |
| schedule_to | activities.schedule_to | date |
| created_at | activities.created_at | date |

**View permission scoping:** Filters where `activities.user_id` OR `activity_participants.user_id` is in authorized IDs.

---

## Business Rules

1. **Notes are auto-done:** `is_done = 1` is forced for `type = 'note'`.
2. **Meeting overlap check:** When creating/updating a `meeting`, `isDurationOverlapping` checks for time range conflicts among all participants (users + persons). If overlap found, returns 400.
   - Overlap algorithm: existing activity's `schedule_from <= new.schedule_from <= existing.schedule_to` OR `existing.schedule_from` is between new start and end.
3. **File storage path:** `activities/{activityId}/{original_filename}`.
4. **Download endpoint:** Uses `FileRepository::findOrFail($id)` then `Storage::download($file->path)` — downloads `activity_files` records, not the activity itself.
5. **Lead sync on update:** If `lead_id` key is present in update data (even empty), the lead association is synced via `$activity->leads()->sync([$lead_id])` or `sync([])` if empty.
6. **Events fired:** `activity.create.before/after`, `activity.update.before/after`, `activity.delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View list | `activities` |
| Create | `activities.create` |
| Edit | `activities.edit` |
| Mass update | `activities.edit` |
| Delete | `activities.delete` |
| Mass delete | `activities.delete` |
