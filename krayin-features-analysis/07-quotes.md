# Quotes

## User Stories

- As a user I can view a list of all quotes with totals and expiry dates.
- As a user I can create a quote, optionally pre-filling it from a lead's products.
- As a user I can add line items to a quote, each with a product, quantity, price, discount, and tax.
- As a user I can set billing and shipping addresses on a quote.
- As a user I can set discount, tax, and adjustment amounts at the quote level.
- As a user I can view and edit an existing quote.
- As a user I can print/download a quote as PDF.
- As a user I can send a quote by email to the associated person.
- As a user I can search for quotes by keyword (autocomplete).
- As a user I can delete a quote.
- As a user I can mass-delete quotes.
- As a user I can get the lead's products formatted as quote items.

---

## API Endpoints

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.quotes.index | `GET /quotes` | List (DataGrid) |
| GET | admin.quotes.create | `GET /quotes/create/{lead_id?}` | Create form (optional lead pre-fill) |
| POST | admin.quotes.store | `POST /quotes/create` | Create quote |
| GET | admin.quotes.edit | `GET /quotes/edit/{id?}` | Edit form |
| PUT | admin.quotes.update | `PUT /quotes/edit/{id}` | Update quote |
| GET | admin.quotes.print | `GET /quotes/print/{id?}` | Download PDF |
| DELETE | admin.quotes.delete | `DELETE /quotes/{id}` | Delete |
| GET | admin.quotes.search | `GET /quotes/search` | Autocomplete search |
| GET | admin.quotes.lead_products | `GET /quotes/lead-products/{leadId}` | Get lead products as quote items |
| POST | admin.quotes.mass_delete | `POST /quotes/mass-destroy` | Mass delete |
| POST | admin.leads.quotes.mail | `POST /leads/quotes/{quoteId}/mail` | Email quote to person |

---

### Request Bodies

**POST/PUT quote (AttributeForm — entity_type = 'quotes'):**

Core fields (not quick_add):
```
subject             string     required
description         string     optional
billing_address     json       optional  {street, city, state, country, postcode}
shipping_address    json       optional  same structure
discount_percent    decimal    optional  0-100
discount_amount     decimal    optional
tax_amount          decimal    optional
adjustment_amount   decimal    optional
sub_total           decimal    optional  usually computed client-side
grand_total         decimal    optional  usually computed client-side
expired_at          datetime   optional
person_id           int        required  FK → persons
user_id             int        required  FK → users
lead_id             int        optional  link to lead
```

**Line items (required unless `quick_add`):**
```
items                         array    required
items.*.product_id            int      required  exists:products,id
items.*.quantity              numeric  required  min 0
items.*.price                 numeric  required  min 0
items.*.total                 numeric  required  min 0  (price * quantity)
items.*.discount_amount       numeric  required  min 0
items.*.tax_amount            numeric  required  min 0
items.*.final_total           numeric  required  min 0
```

Also accepts custom attribute values for `entity_type = 'quotes'`.

**Additional `additionalValidation` (called on create without `quick_add` and all updates):**
Validates items array as above.

---

### GET /quotes/create/{lead_id}

If `lead_id` provided:
- Pre-fills `person_id`, `user_id`, `billing_address` (from person's org address), `expired_at` (from lead's expected_close_date or today).
- Pre-loads lead products as quote item payload.

### GET /quotes/lead-products/{leadId}

Returns lead's products formatted as quote items:
```json
{
  "data": [
    {
      "id": null,
      "product_id": 5,
      "name": "Widget",
      "quantity": 2.0,
      "total": 100.0,
      "price": 50.0,
      "discount_amount": 0,
      "tax_amount": 0
    }
  ]
}
```

### GET /quotes/print/{id}

Renders `admin::quotes.pdf` Blade template and returns PDF download.
Filename: `Quote_{subject}_{created_at formatted as d-m-Y}.pdf`

### POST /leads/quotes/{quoteId}/mail

Emails the quote PDF to the person linked to the quote. (Uses `PDFHandler` trait.)

---

## DB Schema

### `quotes`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| subject | varchar | no | | |
| description | varchar | yes | | |
| billing_address | json | yes | | |
| shipping_address | json | yes | | |
| discount_percent | decimal(12,4) | yes | 0 | |
| discount_amount | decimal(12,4) | yes | | |
| tax_amount | decimal(12,4) | yes | | |
| adjustment_amount | decimal(12,4) | yes | | |
| sub_total | decimal(12,4) | yes | | sum of item totals |
| grand_total | decimal(12,4) | yes | | sub_total - discount + tax + adjustment |
| expired_at | datetime | yes | | |
| person_id | int unsigned | no | | FK → persons ON DELETE CASCADE |
| user_id | int unsigned | no | | FK → users ON DELETE CASCADE |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `quote_items`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| sku | varchar | yes | | copied from product at time of quoting |
| name | varchar | yes | | copied from product |
| quantity | int | yes | 0 | |
| price | decimal(12,4) | no | 0 | unit price |
| coupon_code | varchar | yes | | |
| discount_percent | decimal(12,4) | yes | 0 | |
| discount_amount | decimal(12,4) | yes | 0 | |
| tax_percent | decimal(12,4) | yes | 0 | |
| tax_amount | decimal(12,4) | yes | 0 | |
| total | decimal(12,4) | no | 0 | price * quantity |
| product_id | int unsigned | no | | FK → products (no cascade defined) |
| quote_id | int unsigned | no | | FK → quotes ON DELETE CASCADE |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `lead_quotes`

| Column | Type | Notes |
|--------|------|-------|
| lead_id | int unsigned | FK → leads |
| quote_id | int unsigned | FK → quotes |

---

## Filtering & Sorting (DataGrid)

| Index | DB Column | Filter Type |
|-------|-----------|-------------|
| subject | quotes.subject | text (searchable, sortable) |
| sales_person | users.name | searchable_dropdown (UserRepository) |
| person_name | persons.name | searchable_dropdown (PersonRepository) |
| sub_total | quotes.sub_total | text (sortable, filterable) |
| discount_amount | quotes.discount_amount | text |
| tax_amount | quotes.tax_amount | text |
| adjustment_amount | quotes.adjustment_amount | text |
| grand_total | quotes.grand_total | text |
| expired_at | quotes.expired_at | date (sortable, filterable) |
| created_at | quotes.created_at | date |

**Expired quotes filter:** `expired_quotes.in = 1` shows expired quotes (`DATEDIFF(NOW(), expired_at) >= 0`).

**View permission scoping:** filters `quotes.user_id` by authorized user IDs.

---

## Business Rules

1. **Lead linkage:** A quote can be linked to a lead via `lead_quotes` pivot. On update, existing lead link is fully replaced.
2. **Item management:** On update, new items (prefixed with `item_`) are created; existing items (numeric IDs) are updated; items no longer in the payload are deleted.
3. **Lead product sync:** When updating a quote item and a `lead_id` is present, `lead_products` is also upserted to keep lead products in sync with quote items.
4. **Lead product cleanup:** When a quote item is deleted during update, the corresponding `lead_products` entry is also deleted.
5. **Totals are computed client-side:** The `sub_total` and `grand_total` are submitted in the request; they are not recomputed server-side.
6. **PDF rendering:** Uses `Webkul\Core\Traits\PDFHandler::downloadPDF()` which renders a Blade view and returns a download response.
7. **Events fired:** `quote.create.before/after`, `quote.update.before/after`, `quote.delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View list | `quotes` |
| Create | `quotes.create` |
| Edit | `quotes.edit` |
| Print PDF | `quotes.print` |
| Mail quote | `quotes.mail` |
| Delete | `quotes.delete` |
| Mass delete | `quotes.delete` |
