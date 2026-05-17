# Permission Model — Reconciled Design

> The previous draft proposed a Krayin-style nested tree (`settings.user.users.delete`). The current code uses a flat scheme (`users.delete`). This document reconciles the two, picks one, and lists every key the system will recognize.

## 1. What's in code today

`identity/IdentityPermissions.kt`, `crm/CrmPermissions.kt`, `inventory/InventoryPermissions.kt`, `settings/SettingsPermissions.kt` collectively define:

```
dashboard?              -- (not yet defined; UI assumes it)
users, users.view, users.edit, users.delete
roles, roles.view, roles.edit
groups, groups.view, groups.edit, groups.delete

leads, leads.view, leads.edit, leads.delete                 -- missing: leads.create
contacts, contacts.view, contacts.edit, contacts.delete
activities, activities.view, activities.edit, activities.delete
quotes, quotes.view, quotes.edit, quotes.delete             -- missing: quotes.create, quotes.print, quotes.mail
mail, mail.view, mail.edit                                  -- missing: mail.compose, per-folder, mail.delete
pipelines, pipelines.view, pipelines.edit
tags, tags.view, tags.edit, tags.delete
reports, reports.view

attributes, attributes.view, attributes.edit, attributes.delete
automations, automations.view, automations.edit, automations.delete
marketing, marketing.view, marketing.edit, marketing.delete
imports, imports.view, imports.edit
products, products.view, products.edit, products.delete
warehouses, warehouses.view, warehouses.edit, warehouses.delete
settings, settings.view, settings.edit
```

The hierarchy rule, implemented in `UserService.expandAuthorities()`, is: **if a role has key `X`, it implicitly has every key `X.Y` that exists in the permission catalog**. So a role with `leads` has `leads.view`, `leads.edit`, `leads.delete`. This is the same hierarchical rule Krayin uses.

## 2. What Krayin uses

A deeply nested tree of ~75 keys (see `krayin-features-analysis/15-permissions.md`):

```
settings.user.users.create
settings.user.users.edit
settings.user.users.delete
...
settings.automation.data_transfer.imports.create
```

## 3. Decision: stay flat, fill the gaps

**Keep the current flat scheme. Don't adopt Krayin's nested settings keys.**

Reasons:

- The deep nesting buys nothing the hierarchy rule doesn't already give. Granting `settings.automation` already grants `settings.automation.workflows.create` etc. Flatter keys grant the same with `automations` → `automations.create`.
- Deep keys are noisier in JWT claims and harder to audit visually.
- The Krayin hierarchy mixes "domain" (e.g. `leads`) with "shipped UI section" (e.g. `settings.automation`). The latter is a UI organization concern that has no business affecting back-end authorisation.

But fix the gaps:

- Add `.create` everywhere `.edit` is currently doubling as create-and-update.
- Add the missing per-folder `mail.*` keys (they're real semantic distinctions: a viewer who can read inbox shouldn't necessarily read drafts).
- Add `quotes.print`, `quotes.mail` for the PDF / send actions.
- Add `dashboard` so the dashboard endpoint can be gated.
- Add the cross-tenant keys (see `03-cross-company-sharing.md` § 10).

## 4. Authoritative permission catalog

This is the **complete** list. Every key is implemented by exactly one permission registry (`*PermissionRegistry`) and bootstrapped automatically.

```
dashboard

# Identity
users
├── users.view
├── users.create
├── users.edit
└── users.delete
roles
├── roles.view
├── roles.create
├── roles.edit
└── roles.delete
groups
├── groups.view
├── groups.create
├── groups.edit
└── groups.delete

# CRM core
leads
├── leads.view
├── leads.create
├── leads.edit
└── leads.delete
contacts.persons
├── contacts.persons.view
├── contacts.persons.create
├── contacts.persons.edit
└── contacts.persons.delete
contacts.organizations
├── contacts.organizations.view
├── contacts.organizations.create
├── contacts.organizations.edit
└── contacts.organizations.delete
activities
├── activities.view
├── activities.create
├── activities.edit
└── activities.delete
quotes
├── quotes.view
├── quotes.create
├── quotes.edit
├── quotes.print
├── quotes.mail
└── quotes.delete
mail
├── mail.inbox
├── mail.draft
├── mail.outbox
├── mail.sent
├── mail.trash
├── mail.compose
├── mail.view                     -- view individual email regardless of folder
├── mail.edit                     -- mark read, move folder, tag
└── mail.delete

# Inventory
products
├── products.view
├── products.create
├── products.edit
└── products.delete
warehouses
├── warehouses.view
├── warehouses.create
├── warehouses.edit
└── warehouses.delete

# Configuration
pipelines
├── pipelines.view
├── pipelines.create
├── pipelines.edit
└── pipelines.delete
tags
├── tags.view
├── tags.create
├── tags.edit
└── tags.delete
attributes
├── attributes.view
├── attributes.create
├── attributes.edit
└── attributes.delete
email-templates
├── email-templates.view
├── email-templates.create
├── email-templates.edit
└── email-templates.delete
lead-sources
├── lead-sources.view
├── lead-sources.create
├── lead-sources.edit
└── lead-sources.delete
lead-types
├── lead-types.view
├── lead-types.create
├── lead-types.edit
└── lead-types.delete

# Automation
workflows
├── workflows.view
├── workflows.create
├── workflows.edit
└── workflows.delete
webhooks
├── webhooks.view
├── webhooks.create
├── webhooks.edit
└── webhooks.delete
marketing.events
├── marketing.events.view
├── marketing.events.create
├── marketing.events.edit
└── marketing.events.delete
marketing.campaigns
├── marketing.campaigns.view
├── marketing.campaigns.create
├── marketing.campaigns.edit
└── marketing.campaigns.delete
imports
├── imports.view
├── imports.create
├── imports.edit
├── imports.run
└── imports.delete
web-forms
├── web-forms.view
├── web-forms.create
├── web-forms.edit
└── web-forms.delete

# Reports
reports
└── reports.view

# Cross-tenant (introduced in Phase 2)
relationships
├── relationships.view
└── relationships.manage
share-policies
├── share-policies.view
└── share-policies.manage
records
├── records.share
└── records.reshare

# System
configuration
└── configuration.edit                 -- system_configs table writes
```

Hierarchical inheritance is preserved: granting `marketing` grants `marketing.events.*` and `marketing.campaigns.*`. Granting `contacts` grants both `contacts.persons.*` and `contacts.organizations.*`. The `expandAuthorities()` helper at `UserService.kt:222-232` already supports this; nothing to change.

## 5. Super-admin bypass

Add `Role.permissionType` enum (P0-4 in verification findings):

```kotlin
enum class RoleType { ALL, CUSTOM }

class Role : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var permissionType: RoleType = RoleType.CUSTOM

    // permissions only consulted when permissionType == CUSTOM
    @ManyToMany(...) val permissions: MutableSet<Permission> = mutableSetOf()
}
```

In `UserService.toCredentials()`:

```kotlin
val expanded =
    if (roles.any { it.permissionType == RoleType.ALL }) listOf("*")
    else expandAuthorities(roles.flatMap { it.permissions }.map { it.key }, permissionRepository.findAllKeys())
```

The wildcard `*` is handled by a Spring Security `PermissionEvaluator` so `@PreAuthorize("hasAuthority('leads.view')")` returns true if the authorities list contains `"*"`. Concretely:

```kotlin
@Component
class WildcardPermissionEvaluator : PermissionEvaluator {
    override fun hasPermission(auth: Authentication, target: Any?, perm: Any?): Boolean =
        auth.authorities.any { it.authority == "*" || it.authority == perm }

    override fun hasPermission(auth: Authentication, id: Serializable?, type: String?, perm: Any?) =
        hasPermission(auth, null, perm)
}
```

Wire it via a `MethodSecurityExpressionHandler` bean. The cleanest way to keep `@PreAuthorize("hasAuthority('leads.view')")` syntax is a custom `GrantedAuthority` predicate that treats `"*"` as matching everything — implementable as a single `SimpleGrantedAuthority`-replacement class.

## 6. View permissions (data scoping)

`User.viewPermission` controls which rows the user sees from their own tenant. Three values:

| Value | Behaviour |
|---|---|
| `GLOBAL` | Sees all records the tenant owns. |
| `GROUP` | Sees records whose `userId` belongs to any user in the same `groups` as the requester. |
| `INDIVIDUAL` | Sees only records whose `userId` is themselves. |

**Collapse the redundant `ALL` value** in `ViewPermission.kt` (currently `ALL` and `GLOBAL` resolve identically in `UserService.resolveViewContext`). Pick one name; drop the other. Recommendation: drop `ALL`, keep `GLOBAL`, because it matches Krayin.

`resolveViewContext()` returns a `Set<UUID>?` of userIds the requester may see, or `null` to mean unrestricted. Every list/search/filter method on a scoped entity must consume it. Today only `LeadService.findAll`, `LeadService.filter`, `PersonService`, `OrganizationService`, and `QuoteService` do — and `LeadService.search()` skips it. Audit and fix in Phase 1 (P1-1).

### 6.1 Scoping in the presence of cross-tenant shares

When a record is visible via a share (`tenant_id != currentTenant`), view scoping is **not** applied — the share itself is the authorisation. View scoping is a per-tenant ownership filter; it doesn't apply across tenant boundaries.

Operationally:

```kotlin
fun findAll(pageable: Pageable): PageResponse<LeadResponse> {
    val ownTenantScope = resolveScope()   // null = no view restriction
    val sharedIds = sharedResourceFinder.idsFor(LEADS) // ids visible from other tenants
    return leadRepository.findOwnOrShared(ownTenantScope, sharedIds, pageable)
}
```

This is the only place the application code needs to know about sharing. The DB enforces the wider boundary via RLS.

## 7. Default roles seeded per tenant

When a tenant is provisioned (P0-3), the following are inserted:

| Role | `permissionType` | Notes |
|---|---|---|
| `ADMIN` | `ALL` | Always full access; doesn't break when new permissions are added. |
| `MANAGER` | `CUSTOM` | All non-destructive permissions: every key except `*.delete`, `users.create`, `roles.*`. Plus `relationships.view`, `share-policies.view`, `records.share`. |
| `SALESPERSON` | `CUSTOM` | `leads.*`, `contacts.*`, `activities.*`, `quotes.*` (no delete), `mail.*` (no delete), `tags.view`, `pipelines.view`, `products.view`, `reports.view`, `records.share`. |
| `VIEWER` | `CUSTOM` | Every `*.view` key plus `dashboard`. |

These are seeded inside the new tenant's `tenant_id`. `TenantProvisioningService.provision(...)` is responsible (see P0-3).

## 8. Frontend gating

`web/app/composables/usePermissions.ts` already supports parent-key prefix matching. After the additions in §4, audit every menu item and button to map to one of the keys above. Specifically, "Create lead" buttons should now check `leads.create`, not `leads.edit`.

## 9. Permission migration

Adding new keys (e.g. `leads.create`, `quotes.print`) is non-destructive: bootstrap inserts them, ADMIN roles with `permissionType = ALL` pick them up automatically. CUSTOM roles need to be backfilled. The migration:

```sql
-- V0NN__add_create_permission_keys.sql
-- Adds the new .create keys to any existing CUSTOM role that already has the .edit key.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p_new.id
FROM role_permissions rp
JOIN permissions p_old ON p_old.id = rp.permission_id AND p_old.key = 'leads.edit'
JOIN permissions p_new ON p_new.key = 'leads.create'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2
    WHERE rp2.role_id = rp.role_id AND rp2.permission_id = p_new.id
);
-- Repeat per (.edit → .create) pair.
```

Do this in a single migration after all the new permissions are seeded.

## 10. Endpoint mapping (sample)

Update controllers to use the right permission per action:

| Endpoint | Old `@PreAuthorize` | New |
|---|---|---|
| `POST /leads` | `leads.edit` | `leads.create` |
| `PUT /leads/{id}` | `leads.edit` | `leads.edit` |
| `POST /quotes/{id}/print` | `quotes.edit` | `quotes.print` |
| `POST /quotes/{id}/send-mail` | `quotes.edit` | `quotes.mail` |
| `POST /mail/compose` | `mail.edit` | `mail.compose` |
| `GET /mail?folder=draft` | `mail.view` | `mail.draft` |
| `GET /dashboard/stats` | (none) | `dashboard` |
| `POST /api/relationships` | (n/a) | `relationships.manage` |

A full table belongs in the implementation PR; this is the pattern.
