# Products

## User Stories

- As a user I can view a list of all products with inventory totals.
- As a user I can create a new product with SKU, name, description, quantity, and price.
- As a user I can view a product's detail page.
- As a user I can edit a product's details.
- As a user I can search for products by keyword (autocomplete).
- As a user I can delete a product.
- As a user I can mass-delete products.
- As a user I can attach/detach tags on a product.
- As a user I can view activities associated with a product.
- As a user I can view warehouse inventory grouped by warehouse for a product.
- As a user I can update a product's inventory levels per warehouse location.

---

## API Endpoints

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.products.index | `GET /products` | List (DataGrid) |
| GET | admin.products.create | `GET /products/create` | Create form |
| POST | admin.products.store | `POST /products/create` | Create product |
| GET | admin.products.view | `GET /products/view/{id}` | Detail page |
| GET | admin.products.edit | `GET /products/edit/{id}` | Edit form (includes inventory) |
| PUT | admin.products.update | `PUT /products/edit/{id}` | Update product |
| GET | admin.products.search | `GET /products/search` | Autocomplete search |
| GET | admin.products.warehouses | `GET /products/{id}/warehouses` | Inventory grouped by warehouse |
| POST | admin.products.inventories.store | `POST /products/{id}/inventories/{warehouseId?}` | Save inventory for a product |
| DELETE | admin.products.delete | `DELETE /products/{id}` | Delete product |
| POST | admin.products.mass_delete | `POST /products/mass-destroy` | Mass delete |

### Product Tags

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| POST | admin.products.tags.attach | `POST /products/{id}/tags` | Attach tag |
| DELETE | admin.products.tags.detach | `DELETE /products/{id}/tags` | Detach tag |

### Product Activities

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.products.activities.index | `GET /products/{id}/activities` | Activities for product |

---

### Request Bodies

**POST/PUT product (AttributeForm — entity_type = 'products'):**
```
sku          string   required  unique in products
name         string   optional
description  string   optional
quantity     int      optional  default 0
price        decimal  optional
```
Also accepts any custom attribute values for `entity_type = 'products'`.

**POST /products/{id}/inventories/{warehouseId?}:**
```
inventories                          array    required
inventories.*.warehouse_location_id  int      required  FK → warehouse_locations
inventories.*.warehouse_id           int      required  FK → warehouses
inventories.*.in_stock               int      required  min 0
inventories.*.allocated              int      required  min 0
```

**GET /products/search:**
Query params: standard `RequestCriteria` search params, plus:
- `exclude_ids` — comma-separated or array of product IDs to exclude from results.

**GET /products/{id}/warehouses:**
Returns inventory grouped by warehouse:
```json
[
  {
    "warehouse": {...},
    "locations": [
      {
        "id": 1,
        "warehouse_location_id": 1,
        "name": "Shelf A",
        "in_stock": 100,
        "allocated": 20
      }
    ]
  }
]
```

---

## DB Schema

### `products`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| sku | varchar | no | | unique |
| name | varchar | yes | | |
| description | varchar | yes | | |
| quantity | int | no | 0 | base quantity (legacy; actual stock managed via inventories) |
| price | decimal(12,4) | yes | | |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `product_inventories`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| in_stock | int | no | 0 | |
| allocated | int | no | 0 | |
| product_id | int unsigned | no | | FK → products ON DELETE CASCADE |
| warehouse_id | int unsigned | yes | | FK → warehouses ON DELETE CASCADE |
| warehouse_location_id | int unsigned | yes | | FK → warehouse_locations ON DELETE SET NULL |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `product_tags`

| Column | Type | Notes |
|--------|------|-------|
| tag_id | int unsigned | FK → tags ON DELETE CASCADE |
| product_id | int unsigned | FK → products ON DELETE CASCADE |

### `product_activities`

| Column | Type | Notes |
|--------|------|-------|
| activity_id | int unsigned | FK → activities ON DELETE CASCADE |
| product_id | int unsigned | FK → products ON DELETE CASCADE |

---

## Filtering & Sorting (DataGrid)

| Index | DB Column | Filter Type |
|-------|-----------|-------------|
| sku | products.sku | text (searchable, sortable) |
| name | products.name | text (searchable, sortable) |
| price | products.price | text (searchable, sortable) |
| total_in_stock | SUM(product_inventories.in_stock) | computed (sortable) |
| total_allocated | SUM(product_inventories.allocated) | computed (sortable) |
| total_on_hand | SUM(in_stock - allocated) | computed (sortable) |
| tag_name | tags.name | searchable_dropdown (TagRepository) |

The DataGrid can also be scoped to a warehouse when accessed from the warehouse view (`/products?id={warehouseId}` or route param).

---

## Business Rules

1. **SKU uniqueness:** `sku` must be unique across all products.
2. **Inventory on-hand:** `total_on_hand = total_in_stock - total_allocated`.
3. **Inventory save:** `saveInventories` method in `ProductRepository` upserts inventory records per `warehouse_location_id`. Can be scoped to a specific `warehouseId`.
4. **Edit page includes inventory:** The edit form loads existing inventory with location names via eager-loading.
5. **Exclude IDs in search:** The search endpoint supports `exclude_ids` to prevent already-selected products from showing in autocomplete results.
6. **Events fired:** `product.create.before/after`, `product.update.before/after`, `product.delete.before/after`, `settings.products.delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View list | `products` |
| Create | `products.create` |
| Quick-add | `products.create.quick-create` |
| View detail | `products.view` |
| Edit | `products.edit` |
| Delete | `products.delete` |
| Mass delete | `products.delete` |
