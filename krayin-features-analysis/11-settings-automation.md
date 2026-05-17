# Settings — Automation: Workflows and Webhooks

## User Stories

### Workflows
- As a user I can list all workflows.
- As a user I can create a workflow that triggers on an event, evaluates conditions, and executes actions.
- As a user I can edit a workflow.
- As a user I can delete a workflow.

### Webhooks
- As a user I can list all webhooks.
- As a user I can create a webhook that fires on entity events with configurable HTTP method, URL, headers, and payload.
- As a user I can edit a webhook.
- As a user I can delete a webhook.

---

## API Endpoints

### Workflows

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.settings.workflows.index | `GET /settings/workflows` | List (DataGrid) |
| GET | admin.settings.workflows.create | `GET /settings/workflows/create` | Create form |
| POST | admin.settings.workflows.store | `POST /settings/workflows/create` | Create workflow |
| GET | admin.settings.workflows.edit | `GET /settings/workflows/edit/{id?}` | Edit form |
| PUT | admin.settings.workflows.update | `PUT /settings/workflows/edit/{id}` | Update workflow |
| DELETE | admin.settings.workflows.delete | `DELETE /settings/workflows/{id}` | Delete |

### Webhooks

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.settings.webhooks.index | `GET /settings/webhooks` | List (DataGrid) |
| GET | admin.settings.webhooks.create | `GET /settings/webhooks/create` | Create form |
| POST | admin.settings.webhooks.store | `POST /settings/webhooks/create` | Create webhook |
| GET | admin.settings.webhooks.edit | `GET /settings/webhooks/edit/{id?}` | Edit form |
| PUT | admin.settings.webhooks.update | `PUT /settings/webhooks/edit/{id}` | Update webhook |
| DELETE | admin.settings.webhooks.delete | `DELETE /settings/webhooks/{id}` | Delete |

---

### Workflow Request Body

**POST/PUT /settings/workflows:**
```
name            string   required
description     string   optional
entity_type     string   required  leads | persons | organizations | products | quotes | warehouses
event           string   required  see events below
condition_type  string   optional  default 'and'  values: and | or
conditions      json     optional  array of condition objects
actions         json     optional  array of action objects
```

**Condition object structure:**
```json
{
  "attribute": "lead_value",
  "operator": ">",
  "value": "1000"
}
```

**Action object structure:**
```json
{
  "id": "send_email_to_person",
  "email_template_id": 3
}
```

**Supported events (from system config/automation):**
- `lead.create.after`
- `lead.update.after`
- `lead.delete.after`
- `contacts.person.create.after`
- `contacts.person.update.after`
- `contacts.organization.create.after`
- `contacts.organization.update.after`
- `email.create.after`
- `activity.create.after`

**Common actions:**
- `send_email_to_person` — sends an email template to the associated person
- `send_email_to_agent` — sends an email template to the assigned user
- `assign_stage` — moves lead to a stage
- `assign_group` — assigns lead to a group
- `assign_user` — assigns lead to a user
- `update_attribute` — updates an attribute value

---

### Webhook Request Body (WebhookRequest validation)

**POST/PUT /settings/webhooks:**
```
name             string   required  max 255
entity_type      string   required  max 255
description      string   optional  max 255
method           string   required  max 255  HTTP method (GET, POST, PUT, etc.)
end_point        string   required  max 255  URL to call
query_params     nullable JSON of key-value pairs
headers          nullable JSON of key-value pairs
payload_type     string   required  in: default | x-www-form-urlencoded | raw
raw_payload_type string   optional  in: json | text
payload          nullable JSON payload definition
```

---

## DB Schema

### `workflows`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| name | varchar | no | | |
| description | varchar | yes | | |
| entity_type | varchar | no | | leads / persons / etc. |
| event | varchar | no | | event string e.g. 'lead.create.after' |
| condition_type | varchar | no | and | `and` or `or` |
| conditions | json | yes | | array of condition objects |
| actions | json | yes | | array of action objects |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `webhooks`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | bigint unsigned | no | auto | PK |
| name | varchar | no | | |
| entity_type | varchar | no | | |
| description | varchar | yes | | |
| method | varchar | no | | HTTP method |
| end_point | varchar | no | | target URL |
| query_params | json | yes | | array of `{key, value}` pairs |
| headers | json | yes | | array of `{key, value}` pairs |
| payload_type | varchar | no | | default / x-www-form-urlencoded / raw |
| raw_payload_type | varchar | no | | json / text |
| payload | json | yes | | payload template |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

---

## Business Rules

1. **Workflow execution:** Workflows are triggered by Laravel events. The `WorkflowRepository` (or a dedicated listener) listens for the specified `event`, checks `entity_type` matches, evaluates `conditions` (all must match for `and`, any for `or`), then executes `actions`.
2. **Condition operators:** Standard comparison operators — `=`, `!=`, `>`, `<`, `>=`, `<=`, `like`, `!like`.
3. **Webhook HTTP call:** The `end_point` is called with the specified `method`. `query_params` are appended as URL params, `headers` set on the request, `payload` sent as body. `payload_type` determines encoding.
4. **Events fired:** `settings.workflow.create/update/delete.before/after`, `settings.webhook.create/update/delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View workflows list | `settings.automation.workflows` |
| Create workflow | `settings.automation.workflows.create` |
| Edit workflow | `settings.automation.workflows.edit` |
| Delete workflow | `settings.automation.workflows.delete` |
| View webhooks list | `settings.automation.webhooks` |
| Create webhook | `settings.automation.webhooks.create` |
| Edit webhook | `settings.automation.webhooks.edit` |
| Delete webhook | `settings.automation.webhooks.delete` |
