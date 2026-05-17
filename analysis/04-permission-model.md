# Permission Model — Complete Design

## Current State

The existing permission system works as follows:

1. **Permissions** are global records (no tenant_id) with a `key` string (e.g., `leads.view`)
2. **Roles** are tenant-scoped and have a `permissions` JSON array of allowed keys
3. **Users** have many-to-many `user_roles`; a user has all permissions from all their roles
4. **ViewPermission** enum on user controls data scoping: `GLOBAL | GROUP | INDIVIDUAL`
5. Spring Security `@PreAuthorize("hasAuthority('leads.view')")` gates endpoints

This is solid. The gaps are:
- Permission keys don't fully match the intended design (Krayin vs our needs)
- The cross-company sharing permissions don't exist yet
- Some endpoint guards are missing

---

## Full Permission Key Tree (Recommended)

This aligns closely with Krayin but adjusts for our multi-role system and REST API design.

```
dashboard                                    -- view dashboard

leads                                        -- leads module access
├── leads.view                               -- list + search + kanban
├── leads.create                             -- create new lead
├── leads.edit                               -- edit lead, move stage, manage products/tags
└── leads.delete                             -- delete, mass-delete

quotes                                       -- quotes module
├── quotes.view                              -- list + search
├── quotes.create                            -- create quote
├── quotes.edit                              -- edit quote, update status
├── quotes.print                             -- download PDF
├── quotes.mail                              -- send quote by email
└── quotes.delete                            -- delete, mass-delete

mail                                         -- email module
├── mail.inbox                               -- view inbox folder
├── mail.draft                               -- view drafts folder
├── mail.outbox                              -- view outbox folder
├── mail.sent                                -- view sent folder
├── mail.trash                               -- view trash folder
├── mail.compose                             -- send new email
├── mail.view                                -- view individual email
├── mail.edit                                -- update, move folder, mark read
└── mail.delete                              -- delete / trash

activities                                   -- activities module
├── activities.view                          -- list activities
├── activities.create                        -- create activity
├── activities.edit                          -- update, toggle done, manage participants
└── activities.delete                        -- delete, mass-delete

contacts                                     -- contacts module
├── contacts.persons                         -- access persons
│   ├── contacts.persons.view                -- list + search
│   ├── contacts.persons.create              -- create person
│   ├── contacts.persons.edit                -- edit person, manage tags
│   └── contacts.persons.delete             -- delete, mass-delete
└── contacts.organizations                   -- access organizations
    ├── contacts.organizations.view          -- list + search
    ├── contacts.organizations.create        -- create org
    ├── contacts.organizations.edit          -- edit org
    └── contacts.organizations.delete       -- delete

products                                     -- products module
├── products.view                            -- list + search + detail
├── products.create                          -- create product
├── products.edit                            -- edit, manage inventory, manage tags
└── products.delete                          -- delete, mass-delete

settings                                     -- settings module
├── settings.users                           -- user management
│   ├── settings.users.view                  -- list users
│   ├── settings.users.create                -- create user
│   ├── settings.users.edit                  -- edit user, mass-update
│   └── settings.users.delete               -- delete user, mass-delete
├── settings.roles                           -- role management
│   ├── settings.roles.view                  -- list roles
│   ├── settings.roles.create                -- create role
│   ├── settings.roles.edit                  -- edit role
│   └── settings.roles.delete               -- delete role
├── settings.groups                          -- group management
│   ├── settings.groups.view                 -- list groups
│   ├── settings.groups.create               -- create group
│   ├── settings.groups.edit                 -- edit group
│   └── settings.groups.delete              -- delete group
├── settings.pipelines                       -- pipeline management
│   ├── settings.pipelines.view              -- list pipelines
│   ├── settings.pipelines.create            -- create pipeline
│   ├── settings.pipelines.edit              -- edit pipeline + stages
│   └── settings.pipelines.delete           -- delete pipeline
├── settings.lead-sources                    -- lead source management
│   ├── settings.lead-sources.create
│   ├── settings.lead-sources.edit
│   └── settings.lead-sources.delete

