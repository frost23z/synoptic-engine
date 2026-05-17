# Contacts — Persons and Organizations

## User Stories

### Persons
- As a user I can list all persons in a sortable, filterable table.
- As a user I can create a new person with name, emails, phone numbers, organization, job title.
- As a user I can view a person's detail page.
- As a user I can edit a person's details.
- As a user I can search for persons by keyword (autocomplete).
- As a user I can delete a person (blocked if person has associated leads).
- As a user I can mass-delete persons (skips those with leads, reports partial results).
- As a user I can attach/detach tags on a person.
- As a user I can view activities associated with a person.

### Organizations
- As a user I can list all organizations.
- As a user I can create a new organization with name and address.
- As a user I can edit an organization.
- As a user I can delete an organization.
- As a user I can mass-delete organizations.
- As a user I can see how many persons belong to each organization.

---

## API Endpoints

### Persons

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.contacts.persons.index | `GET /contacts/persons` | List (DataGrid) |
| GET | admin.contacts.persons.create | `GET /contacts/persons/create` | Create form |
| POST | admin.contacts.persons.store | `POST /contacts/persons/create` | Create person |
| GET | admin.contacts.persons.view | `GET /contacts/persons/view/{id}` | Detail page |
| GET | admin.contacts.persons.edit | `GET /contacts/persons/edit/{id}` | Edit form |
| PUT | admin.contacts.persons.update | `PUT /contacts/persons/edit/{id}` | Update person |
| GET | admin.contacts.persons.search | `GET /contacts/persons/search` | Autocomplete |
| DELETE | admin.contacts.persons.delete | `DELETE /contacts/persons/{id}` | Delete (throttled: 100/60s) |
| POST | admin.contacts.persons.mass_delete | `POST /contacts/persons/mass-destroy` | Mass delete |

### Person Tags

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| POST | admin.contacts.persons.tags.attach | `POST /contacts/persons/{id}/tags` | Attach tag |
| DELETE | admin.contacts.persons.tags.detach | `DELETE /contacts/persons/{id}/tags` | Detach tag |

### Person Activities

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.contacts.persons.activities.index | `GET /contacts/persons/{id}/activities` | List person activities |

### Organizations

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.contacts.organizations.index | `GET /contacts/organizations` | List (DataGrid) |
| GET | admin.contacts.organizations.create | `GET /contacts/organizations/create` | Create form |
| POST | admin.contacts.organizations.store | `POST /contacts/organizations/create` | Create org |
| GET | admin.contacts.organizations.edit | `GET /contacts/organizations/edit/{id?}` | Edit form |
| PUT | admin.contacts.organizations.update | `PUT /contacts/organizations/edit/{id}` | Update org |
| DELETE | admin.contacts.organizations.delete | `DELETE /contacts/organizations/{id}` | Delete |
| PUT | admin.contacts.organizations.mass_delete | `PUT /contacts/organizations/mass-destroy` | Mass delete |

---

### Request Bodies

**POST/PUT person (AttributeForm — entity_type = 'persons'):**

Core fields:
```
name              string    required
emails            json      required, array of {value: string, label: string}
contact_numbers   json      optional, array of {value: string, label: string}
organization_id   int       optional, FK → organizations
job_title         string    optional
user_id           int       optional, FK → users (sales owner)
```
Also accepts any custom attribute values defined for `entity_type = 'persons'`.

**POST/PUT organization (AttributeForm — entity_type = 'organizations'):**
```
name     string  required
address  json    optional, address object
user_id  int     optional, FK → users
```
Also accepts any custom attribute values for `entity_type = 'organizations'`.

---

## DB Schema

### `persons`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| name | varchar | no | | |
| emails | json | no | | array of `{value, label}` objects |
| contact_numbers | json | yes | | array of `{value, label}` objects |
| job_title | varchar | yes | | |
| organization_id | int unsigned | yes | | FK → organizations ON DELETE CASCADE |
| user_id | int unsigned | yes | | FK → users ON DELETE SET NULL |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

Indexes: `unique_id` column (migration `2024_09_09`).

### `organizations`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| name | varchar | no | | unique index |
| address | json | yes | | |
| user_id | int unsigned | yes | | FK → users ON DELETE SET NULL |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `person_tags`

| Column | Type | Notes |
|--------|------|-------|
| tag_id | int unsigned | FK → tags ON DELETE CASCADE |
| person_id | int unsigned | FK → persons ON DELETE CASCADE |

### `person_activities`

| Column | Type | Notes |
|--------|------|-------|
| activity_id | int unsigned | FK → activities ON DELETE CASCADE |
| person_id | int unsigned | FK → persons ON DELETE CASCADE |

---

## Filtering & Sorting (DataGrid)

### Person DataGrid

| Index | DB Column | Filter Type |
|-------|-----------|-------------|
| id | persons.id | integer |
| person_name | persons.name | text (searchable, sortable) |
| emails | persons.emails | text (JSON search) |
| contact_numbers | persons.contact_numbers | text |
| organization | organizations.name | searchable_dropdown (OrganizationRepository) |

**View permission scoping:** filters `persons.user_id` by authorized user IDs.

### Organization DataGrid

| Index | DB Column | Filter Type |
|-------|-----------|-------------|
| id | organizations.id | integer |
| name | organizations.name | text |
| persons_count | computed | not filterable (closure counts via personRepository) |
| created_at | organizations.created_at | date_range |

---

## Business Rules

1. **Person delete blocked by leads:** If a person has associated leads (`person->leads->count() > 0`), deletion returns 400.
2. **Mass-delete partial results:** Returns different messages depending on how many were deleted vs. blocked.
3. **Organization persons count:** The `persons_count` column in the DataGrid is a live count of `persons` with `organization_id = row.id`.
4. **Emails stored as JSON:** The `emails` column holds an array like `[{"value":"user@example.com","label":"work"}]`.
5. **Contact numbers stored as JSON:** Same pattern as emails.
6. **Search uses RequestCriteria:** The search endpoint returns persons filtered by view_permission scoping.
7. **Events fired:** `contacts.person.create.before/after`, `contacts.person.update.before/after`, `contacts.person.delete.before/after`, `contacts.organization.create/update/delete`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View persons list | `contacts.persons` |
| Create person | `contacts.persons.create` |
| Quick-add person | `contacts.persons.create.quick-create` |
| View person | `contacts.persons.view` |
| Edit person | `contacts.persons.edit` |
| Delete person | `contacts.persons.delete` |
| View orgs list | `contacts.organizations` |
| Create org | `contacts.organizations.create` |
| Quick-add org | `contacts.organizations.create.quick-create` |
| Edit org | `contacts.organizations.edit` |
| Delete org | `contacts.organizations.delete` |
