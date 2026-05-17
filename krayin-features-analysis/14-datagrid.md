# DataGrid System

The DataGrid is Krayin's reusable list/table engine. Every list view in the system uses a DataGrid class. Understanding it is critical for reimplementation.

---

## Architecture

Every DataGrid extends `Webkul\DataGrid\DataGrid` (abstract class). Each DataGrid implements:

- `prepareQueryBuilder()` — returns a `DB::table(...)` query builder
- `prepareColumns()` — calls `$this->addColumn(...)` for each column
- `prepareActions()` (optional) — row-level action buttons
- `prepareMassActions()` (optional) — bulk actions for selected rows

The controller calls `datagrid(MyDataGrid::class)->process()` which:
1. Calls `prepare()` internally.
2. Validates request params.
3. Applies filters, sort, and pagination.
4. Returns `response()->json($this->formatData())`.

---

## Request Parameters

A DataGrid AJAX call sends:

```json
{
  "filters": {
    "column_index": ["value1", "value2"],
    "all": ["global search term"]
  },
  "sort": {
    "column": "created_at",
    "order": "desc"
  },
  "pagination": {
    "page": 1,
    "per_page": 10
  },
  "export": false,
  "format": "csv"
}
```

All params are optional. Validation:
```
filters     array   sometimes
sort        array   sometimes
pagination  array   sometimes
export      boolean sometimes
format      string  sometimes  in: csv, xls, xlsx
```

---

## Response Format

```json
{
  "id": "encrypted_class_name",
  "columns": [ ...column definitions... ],
  "actions": [ ...row actions... ],
  "mass_actions": [ ...bulk actions... ],
  "records": [ ...rows with injected actions... ],
  "meta": {
    "primary_column": "id",
    "from": 1,
    "to": 10,
    "total": 47,
    "per_page_options": [10, 20, 30, 40, 50],
    "per_page": 10,
    "current_page": 1,
    "last_page": 5
  }
}
```

---

## Column Definition

Each column is defined with:

```php
$this->addColumn([
    'index'              => 'column_index',   // unique key, maps to filter key
    'label'              => 'Display Label',
    'type'               => 'string',          // integer | string | date | boolean | aggregate
    'searchable'         => true,              // included in global search
    'filterable'         => true,              // can be filtered
    'sortable'           => true,              // can be sorted
    'filterable_type'    => null,              // null | dropdown | searchable_dropdown | date_range
    'filterable_options' => [],                // for dropdown: array of {label, value}
                                               // for searchable_dropdown: {repository, column}
    'closure'            => fn($row) => ...,  // transform the column value for display
]);
```

### Column Types

| Type | Description |
|------|-------------|
| `integer` | Integer filter (exact match or range) |
| `string` | Text filter (LIKE match) |
| `date` | Date filter (exact or date_range) |
| `boolean` | Boolean filter (0/1) |
| `aggregate` | SUM/COUNT derived value |

### Filter Types

| filterable_type | Behavior |
|----------------|----------|
| `null` (default) | Simple text/integer match |
| `dropdown` | Filter from static options list |
| `searchable_dropdown` | Async search via repository |
| `date_range` | `from` and `to` date picker |

### Searchable Dropdown

```php
'filterable_type' => 'searchable_dropdown',
'filterable_options' => [
    'repository' => UserRepository::class,
    'column' => [
        'label' => 'name',   // displayed text
        'value' => 'id',     // submitted filter value
    ],
],
```

The frontend sends a `column_index.in` filter with IDs.

---

## Filter Application

For each filter key in `filters`:

- **`all` key:** Global search — applies LIKE on all `searchable` non-boolean, non-aggregate columns with OR.
- **Specific column key:** Calls `$column->processFilter($queryBuilder, $values)` which applies the appropriate WHERE clause based on column type.

### Filter operators supported (from Column class):

Filters can be sent as:
- Simple value → `WHERE column = value`
- `{in: [1,2,3]}` → `WHERE column IN (...)`
- `{from: date, to: date}` → `WHERE column BETWEEN`
- `{like: '%text%'}` → `WHERE column LIKE`

The column index in the filter payload maps to the DB column via `addFilter()`:
```php
$this->addFilter('sales_person', 'users.name');
// filter key = 'sales_person', DB column = 'users.name'
```

---

## Sort Application

```json
{ "sort": { "column": "created_at", "order": "desc" } }
```

Falls back to `$this->sortColumn` (default: primary column) and `$this->sortOrder` (default: 'desc').

---

## Pagination

```json
{ "pagination": { "page": 1, "per_page": 20 } }
```

Falls back to `$this->itemsPerPage` (default: 10). Per-page options: [10, 20, 30, 40, 50].

---

## Export

If `export: true` in request, calls `$this->setExportFile($format)` instead of paginating. Returns a binary file download (CSV/XLS/XLSX) using `Maatwebsite\Excel`.

---

## Mass Actions

Mass action definitions:
```php
$this->addMassAction([
    'icon'    => 'icon-delete',
    'title'   => 'Delete Selected',
    'method'  => 'POST',
    'url'     => route('admin.leads.mass_delete'),
    'options' => [  // optional: turns into a mass-update with value selection
        ['label' => 'Stage A', 'value' => 1],
    ],
]);
```

The frontend sends selected record IDs as `indices[]` in the POST body.

---

## Row Actions

```php
$this->addAction([
    'index'  => 'edit',
    'icon'   => 'icon-edit',
    'title'  => 'Edit',
    'method' => 'GET',
    'url'    => fn($row) => route('admin.leads.edit', $row->id),
]);
```

Each row in the response has an `actions` array with resolved URLs.

---

## Saved Filters

The `datagrid_saved_filters` table allows users to save filter presets.

### DB Schema: `datagrid_saved_filters`

| Column | Type | Notes |
|--------|------|-------|
| id | bigint | PK |
| user_id | int unsigned | |
| name | varchar | filter set name |
| src | varchar | DataGrid class identifier (encrypted) |
| applied | json | the `filters`, `sort`, `pagination` state |
| created_at | timestamp | |
| updated_at | timestamp | |

Unique constraint: `(user_id, name, src)` — one named filter per user per DataGrid.

### Saved Filter Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `POST /datagrid/saved-filters/store` | Save filter |
| PUT | `PUT /datagrid/saved-filters/update/{id}` | Update filter |
| DELETE | `DELETE /datagrid/saved-filters/destroy/{id}` | Delete filter |

---

## Sanitization

Each row's string values are processed through `strip_tags()` before being included in the JSON response. This prevents XSS from stored data, though closures can re-introduce HTML for formatted columns.

---

## DataGrid Classes Reference

| DataGrid class | Entity | Notes |
|----------------|--------|-------|
| `LeadDataGrid` | Leads | Pipeline-scoped, rotten_lead computed |
| `ActivityDataGrid` | Activities | Only call/meeting/lunch types |
| `PersonDataGrid` | Persons | View permission scoped |
| `OrganizationDataGrid` | Organizations | persons_count closure |
| `EmailDataGrid` | Emails | Folder-filtered |
| `ProductDataGrid` | Products | Inventory aggregates |
| `QuoteDataGrid` | Quotes | View permission scoped |
| `AttributeDataGrid` | Attributes | |
| `PipelineDataGrid` | Pipelines | |
| `UserDataGrid` | Users | |
| `RoleDataGrid` | Roles | |
| `GroupDataGrid` | Groups | |
| `TagDataGrid` | Tags | |
| `SourceDataGrid` | Sources | |
| `TypeDataGrid` | Types | |
| `WorkflowDataGrid` | Workflows | |
| `WebhookDataGrid` | Webhooks | |
| `EmailTemplateDataGrid` | Email Templates | |
| `EventDataGrid` | Marketing Events | |
| `CampaignDatagrid` | Marketing Campaigns | |
| `ImportDataGrid` | Imports | |
| `WarehouseDataGrid` | Warehouses | |
