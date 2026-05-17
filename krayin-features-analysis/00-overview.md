# Krayin CRM — System Overview

## What Krayin Is

Krayin is an open-source Laravel/PHP CRM built by Webkul. It covers the full lifecycle of a B2B sales process: leads, contacts, quotes, products, activities, email, pipelines, and all supporting configuration. The codebase is organized as a set of Laravel modules under the `Webkul` namespace, each responsible for a distinct domain.

## Module List

| Module | Directory | Responsibility |
|--------|-----------|----------------|
| Activity | `Webkul/Activity` | Activities (calls, meetings, notes, files), participants |
| Admin | `Webkul/Admin` | HTTP layer — controllers, DataGrids, request forms, ACL config |
| Attribute | `Webkul/Attribute` | Custom attributes, attribute options, attribute values |
| Automation | `Webkul/Automation` | Workflows and webhooks |
| Contact | `Webkul/Contact` | Persons and organizations |
| Core | `Webkul/Core` | System config (core_config table), utilities, countries |
| DataGrid | `Webkul/DataGrid` | Reusable DataGrid engine and saved filters |
| DataTransfer | `Webkul/DataTransfer` | CSV/Excel import pipeline with batching |
| Email | `Webkul/Email` | Email storage, attachments, folders (inbox/draft/sent/outbox/trash) |
| EmailTemplate | `Webkul/EmailTemplate` | Reusable email templates for marketing and automation |
| Installer | `Webkul/Installer` | Seed migrations for default attributes |
| Lead | `Webkul/Lead` | Leads, pipelines, stages, sources, types, lead products |
| Marketing | `Webkul/Marketing` | Marketing events and campaigns |
| Product | `Webkul/Product` | Products, inventory, product tags, product activities |
| Quote | `Webkul/Quote` | Quotes (header + line items), PDF print |
| Tag | `Webkul/Tag` | Tags, used across leads/persons/emails/products/warehouses |
| User | `Webkul/User` | Users, roles, groups, password reset |
| Warehouse | `Webkul/Warehouse` | Warehouses, locations, warehouse tags, warehouse activities |
| WebForm | `Webkul/WebForm` | Embeddable web forms that create leads/persons |

## Permission System Summary

Krayin uses a custom `Bouncer` class (`Webkul\Admin\Bouncer`) wrapping Laravel's `bouncer()` helper.

### Role model
- A `Role` has a `permission_type`: `all` (super-admin) or `custom`.
- If `custom`, a JSON `permissions` array stores the allowed permission keys.
- Every user has exactly one role (`role_id` FK on `users`).

### View permission (data scoping)
Each user has a `view_permission` column with one of three values:
- `global` — sees all records
- `group` — sees records owned by any member of the user's groups
- `individual` — sees only their own records

The `bouncer()->getAuthorizedUserIds()` method returns `null` (no filter) for global, the group member IDs for group, or `[$currentUserId]` for individual.

### Permission key format
Hierarchical dot-notation: `leads`, `leads.create`, `leads.view`, `leads.edit`, `leads.delete`. See `15-permissions.md` for the full tree.

## Technology Stack

- **Backend:** PHP 8.x / Laravel 11, Eloquent ORM, L5-Repository pattern
- **DataGrid:** custom `Webkul\DataGrid\DataGrid` abstract class — see `14-datagrid.md`
- **PDF:** `Webkul\Core\Traits\PDFHandler` — renders a Blade view to PDF
- **File storage:** Laravel `Storage` facade (local disk)
- **Queue:** Laravel queue (configurable; sync by default)

## Navigation / Menu Structure (from `core_config.php` menu section)

Dashboard → Leads → Quotes → Mail (Inbox / Draft / Outbox / Sent / Trash) → Activities → Contacts (Persons / Organizations) → Products → Settings → Configuration
