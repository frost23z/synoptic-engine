# Complete ACL Permission Tree

This is the full permission listing from `Admin/src/Config/acl.php`. Each entry has a `key` (the permission string used in code), a display `name`, and associated routes.

---

## Permission Key Hierarchy

```
dashboard
│
leads
├── leads.create
├── leads.create.quick-create
├── leads.view
├── leads.edit
└── leads.delete
│
quotes
├── quotes.create
├── quotes.mail
├── quotes.edit
├── quotes.print
└── quotes.delete
│
mail
├── mail.inbox
├── mail.draft
├── mail.outbox
├── mail.sent
├── mail.trash
├── mail.compose
├── mail.compose.quick-create
├── mail.view
├── mail.edit
└── mail.delete
│
activities
├── activities.create
├── activities.edit
└── activities.delete
│
contacts
├── contacts.persons
│   ├── contacts.persons.create
│   ├── contacts.persons.create.quick-create
│   ├── contacts.persons.view
│   ├── contacts.persons.edit
│   └── contacts.persons.delete
└── contacts.organizations
    ├── contacts.organizations.create
    ├── contacts.organizations.create.quick-create
    ├── contacts.organizations.edit
    └── contacts.organizations.delete
│
products
├── products.create
├── products.create.quick-create
├── products.view
├── products.edit
└── products.delete
│
settings
├── settings.user
│   ├── settings.user.groups
│   │   ├── settings.user.groups.create
│   │   ├── settings.user.groups.edit
│   │   └── settings.user.groups.delete
│   ├── settings.user.roles
│   │   ├── settings.user.roles.create
│   │   ├── settings.user.roles.edit
│   │   └── settings.user.roles.delete
│   └── settings.user.users
│       ├── settings.user.users.create
│       ├── settings.user.users.edit
│       └── settings.user.users.delete
├── settings.lead
│   ├── settings.lead.pipelines
│   │   ├── settings.lead.pipelines.create
│   │   ├── settings.lead.pipelines.edit
│   │   └── settings.lead.pipelines.delete
│   ├── settings.lead.sources
│   │   ├── settings.lead.sources.create
│   │   ├── settings.lead.sources.edit
│   │   └── settings.lead.sources.delete
│   └── settings.lead.types
│       ├── settings.lead.types.create
│       ├── settings.lead.types.edit
│       └── settings.lead.types.delete
├── settings.inventory
│   └── settings.inventory.warehouse
│       ├── settings.inventory.warehouse.create
│       ├── settings.inventory.warehouse.edit
│       └── settings.inventory.warehouse.delete
├── settings.automation
│   ├── settings.automation.attributes
│   │   ├── settings.automation.attributes.create
│   │   ├── settings.automation.attributes.edit
│   │   └── settings.automation.attributes.delete
│   ├── settings.automation.email_templates
│   │   ├── settings.automation.email_templates.create
│   │   ├── settings.automation.email_templates.edit
│   │   └── settings.automation.email_templates.delete
│   ├── settings.automation.workflows
│   │   ├── settings.automation.workflows.create
│   │   ├── settings.automation.workflows.edit
│   │   └── settings.automation.workflows.delete
│   ├── settings.automation.events
│   │   ├── settings.automation.events.create
│   │   ├── settings.automation.events.edit
│   │   └── settings.automation.events.delete
│   ├── settings.automation.campaigns
│   │   ├── settings.automation.campaigns.create
│   │   ├── settings.automation.campaigns.edit
│   │   └── settings.automation.campaigns.delete
│   ├── settings.automation.webhooks
│   │   ├── settings.automation.webhooks.create
│   │   ├── settings.automation.webhooks.edit
│   │   └── settings.automation.webhooks.delete
│   └── settings.automation.data_transfer
│       └── settings.automation.data_transfer.imports
│           ├── settings.automation.data_transfer.imports.create
│           ├── settings.automation.data_transfer.imports.edit
│           ├── settings.automation.data_transfer.imports.delete
│           └── settings.automation.data_transfer.imports.import
└── settings.other_settings
    └── settings.other_settings.tags
        ├── settings.other_settings.tags.create
        ├── settings.other_settings.tags.edit
        └── settings.other_settings.tags.delete
│
configuration
```

---

## Flat Permission Key List

```
dashboard
leads
leads.create
leads.create.quick-create
leads.view
leads.edit
leads.delete
quotes
quotes.create
quotes.mail
quotes.edit
quotes.print
quotes.delete
mail
mail.inbox
mail.draft
mail.outbox
mail.sent
mail.trash
mail.compose
mail.compose.quick-create
mail.view
mail.edit
mail.delete
activities
activities.create
activities.edit
activities.delete
contacts
contacts.persons
contacts.persons.create
contacts.persons.create.quick-create
contacts.persons.view
contacts.persons.edit
contacts.persons.delete
contacts.organizations
contacts.organizations.create
contacts.organizations.create.quick-create
contacts.organizations.edit
contacts.organizations.delete
products
products.create
products.create.quick-create
products.view
products.edit
products.delete
settings
settings.user
settings.user.groups
settings.user.groups.create
settings.user.groups.edit
settings.user.groups.delete
settings.user.roles
settings.user.roles.create
settings.user.roles.edit
settings.user.roles.delete
settings.user.users
settings.user.users.create
settings.user.users.edit
settings.user.users.delete
settings.lead
settings.lead.pipelines
settings.lead.pipelines.create
settings.lead.pipelines.edit
settings.lead.pipelines.delete
settings.lead.sources
settings.lead.sources.create
settings.lead.sources.edit
settings.lead.sources.delete
settings.lead.types
settings.lead.types.create
settings.lead.types.edit
settings.lead.types.delete
settings.inventory
settings.inventory.warehouse
settings.inventory.warehouse.create
settings.inventory.warehouse.edit
settings.inventory.warehouse.delete
settings.automation
settings.automation.attributes
settings.automation.attributes.create
settings.automation.attributes.edit
settings.automation.attributes.delete
settings.automation.email_templates
settings.automation.email_templates.create
settings.automation.email_templates.edit
settings.automation.email_templates.delete
settings.automation.workflows
settings.automation.workflows.create
settings.automation.workflows.edit
settings.automation.workflows.delete
settings.automation.events
settings.automation.events.create
settings.automation.events.edit
settings.automation.events.delete
settings.automation.campaigns
settings.automation.campaigns.create
settings.automation.campaigns.edit
settings.automation.campaigns.delete
settings.automation.webhooks
settings.automation.webhooks.create
settings.automation.webhooks.edit
settings.automation.webhooks.delete
settings.automation.data_transfer
settings.automation.data_transfer.imports
settings.automation.data_transfer.imports.create
settings.automation.data_transfer.imports.edit
settings.automation.data_transfer.imports.delete
settings.automation.data_transfer.imports.import
settings.other_settings
settings.other_settings.tags
settings.other_settings.tags.create
settings.other_settings.tags.edit
settings.other_settings.tags.delete
configuration
```

Total: ~75 distinct permission keys.

---

## How Permissions Work in Roles

A role has `permission_type`:
- `all` — bypasses all permission checks (super admin)
- `custom` — `permissions` JSON array contains the list of allowed key strings

`bouncer()->hasPermission('key')` checks:
1. If role.permission_type = 'all' → returns true
2. If role.permission_type = 'custom' → checks if `key` or any parent key is in role.permissions array

Parent key matching means having `leads` also grants access to routes that check `leads.view`, `leads.create`, etc. — the permission check walks up the key hierarchy.

---

## Permission Gating in DataGrid Actions

```php
if (bouncer()->hasPermission('leads.view')) {
    $this->addAction([...]);
}

if (bouncer()->hasPermission('leads.delete')) {
    $this->addAction([...]);
}
```

This means actions only appear in the DataGrid response if the user has the required permission.

---

## Quick-Add vs Create

The `.quick-create` permissions enable a streamlined creation form (fewer fields, opens in a modal). They share the same underlying route as `.create` but are gated separately to allow/deny quick-add independently.

---

## WebForm and Lead Tag Permissions

Tags attach/detach on leads is grouped under `settings.other_settings.tags.create` and `settings.other_settings.tags.delete`. This means users who can manage tags in settings can also attach/detach tags on individual leads.
