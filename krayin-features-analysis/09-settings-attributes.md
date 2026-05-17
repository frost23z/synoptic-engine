# Settings — Custom Attributes

## User Stories

- As a user I can view a list of all custom attributes.
- As a user I can create a new custom attribute for an entity type (leads, contacts, products, etc.).
- As a user I can edit a custom attribute's name, validation, and options.
- As a user I can delete a user-defined attribute (system attributes cannot be deleted).
- As a user I can mass-delete user-defined attributes.
- As a user I can download a file/image stored in an attribute value.
- As a user I can look up attribute options for lookup-type attributes.
- As a user I can check whether a unique-type attribute value is already taken.

---

## API Endpoints

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.settings.attributes.index | `GET /settings/attributes` | List (DataGrid) |
| GET | admin.settings.attributes.check_unique_validation | `GET /settings/attributes/check-unique-validation` | Check if value is unique |
| GET | admin.settings.attributes.create | `GET /settings/attributes/create` | Create form |
| POST | admin.settings.attributes.store | `POST /settings/attributes/create` | Create attribute |
| GET | admin.settings.attributes.edit | `GET /settings/attributes/edit/{id}` | Edit form |
| PUT | admin.settings.attributes.update | `PUT /settings/attributes/edit/{id}` | Update attribute |
| GET | admin.settings.attributes.lookup | `GET /settings/attributes/lookup/{lookup?}` | Lookup options for a lookup type |
| GET | admin.settings.attributes.lookup_entity | `GET /settings/attributes/lookup-entity/{lookup?}` | Look up a single entity |
| DELETE | admin.settings.attributes.delete | `DELETE /settings/attributes/{id}` | Delete (user-defined only) |
| GET | admin.settings.attributes.options | `GET /settings/attributes/{id}/options` | Get options for a select/multiselect |
| POST | admin.settings.attributes.mass_update | `POST /settings/attributes/mass-update` | Mass update attributes |
| POST | admin.settings.attributes.mass_delete | `POST /settings/attributes/mass-destroy` | Mass delete (user-defined only) |
| GET | admin.settings.attributes.download | `GET /settings/attributes/download` | Download file/image attribute value |

---

### Request Bodies

**POST /settings/attributes/create:**
```
code           string   required  unique per (code, entity_type); snake_case format
name           string   required
type           string   required  see attribute types below
entity_type    string   required  leads | persons | organizations | products | quotes | warehouses
lookup_type    string   optional  for lookup-type attributes
validation     string   optional  e.g. 'required', 'numeric', 'email'
is_required    boolean  optional  default 0
is_unique      boolean  optional  default 0
quick_add      boolean  optional  default 0
sort_order     int      optional
options        array    optional  for select/multiselect
  options.*.name       string  option label
  options.*.sort_order int     display order
```

**Code validation:** Uses `Webkul\Core\Contracts\Validations\Code` rule (likely snake_case / alphanumeric-underscore).

**GET /settings/attributes/check-unique-validation:**
```
entity_id        int     the record ID being updated
entity_type      string  entity type
attribute_code   string  the attribute code
attribute_value  mixed   the value to check
```
Returns `{ validated: bool }`.

**GET /settings/attributes/download:**
```
path  string  required  the storage path of the file
```

---

## Attribute Types

From the `type` column in `attributes`. Krayin supports these types (from usage across the codebase):

| Type | Description | Stored In |
|------|-------------|-----------|
| `text` | Single-line text | `text_value` |
| `textarea` | Multi-line text | `text_value` |
| `email` | Email input | `text_value` |
| `phone` | Phone number | `text_value` |
| `boolean` | Checkbox | `boolean_value` |
| `integer` | Integer number | `integer_value` |
| `float` | Float number | `float_value` |
| `date` | Date picker | `date_value` |
| `datetime` | Date+time picker | `datetime_value` |
| `select` | Single-select dropdown | `integer_value` (option ID) |
| `multiselect` | Multi-select | `json_value` (array of option IDs) |
| `lookup` | Lookup from another entity | `integer_value` (entity ID) |
| `image` | Image upload | `text_value` (path) |
| `file` | File upload | `text_value` (path) |
| `address` | JSON address | `json_value` |
| `price` | Monetary decimal | `float_value` |
| `checkbox` | Multiple checkbox | `json_value` |

---

## Entity Types

From `Admin/src/Config/attribute_entity_types.php`:
- `leads`
- `persons`
- `organizations`
- `products`
- `quotes`
- `warehouses`

---

## DB Schema

### `attributes`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| code | varchar | no | | unique composite with entity_type |
| name | varchar | no | | display name |
| type | varchar | no | | see attribute types |
| lookup_type | varchar | yes | | for lookup attributes |
| entity_type | varchar | no | | leads / persons / etc. |
| sort_order | int | yes | | |
| validation | varchar | yes | | validation rule string |
| is_required | tinyint | no | 0 | |
| is_unique | tinyint | no | 0 | |
| quick_add | tinyint | no | 0 | show in quick-add form |
| is_user_defined | tinyint | no | 1 | 0 = system attribute, cannot delete |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

Unique constraint: `(code, entity_type)`.

### `attribute_options`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| name | varchar | yes | display label |
| sort_order | int | yes | |
| attribute_id | int unsigned | no | FK → attributes ON DELETE CASCADE |

### `attribute_values`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| entity_type | varchar | no | default 'leads' |
| text_value | text | yes | |
| boolean_value | tinyint | yes | |
| integer_value | int | yes | |
| float_value | double | yes | |
| datetime_value | datetime | yes | |
| date_value | date | yes | |
| json_value | json | yes | |
| entity_id | int unsigned | no | record ID (polymorphic) |
| attribute_id | int unsigned | no | FK → attributes ON DELETE CASCADE |

Unique constraint: `(entity_type, entity_id, attribute_id)`.

---

## Business Rules

1. **System attributes cannot be deleted:** `is_user_defined = 0` attributes return 400 on delete attempts.
2. **Code uniqueness is per entity_type:** Two attributes for different entities can share the same code.
3. **Mass delete skips system attributes:** Only `is_user_defined = 1` attributes are deleted in mass operations.
4. **Attribute value storage is EAV:** Values are stored in a separate `attribute_values` table using the appropriate typed column (`text_value`, `boolean_value`, etc.).
5. **Unique validation:** For `is_unique = 1` attributes, the value must be unique across all records of the same `entity_type`, excluding the record being updated.
6. **Quick add:** `quick_add = 1` attributes appear in the quick-create form for the entity.
7. **Events fired:** `settings.attribute.create/update/delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View list | `settings.automation.attributes` |
| Create | `settings.automation.attributes.create` |
| Edit | `settings.automation.attributes.edit` |
| Mass update | `settings.automation.attributes.edit` |
| Delete | `settings.automation.attributes.delete` |
| Mass delete | `settings.automation.attributes.delete` |
