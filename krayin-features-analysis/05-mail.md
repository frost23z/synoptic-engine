# Mail / Email

## User Stories

- As a user I can view my email inbox (filtered by folder).
- As a user I can view draft, outbox, sent, and trash folders.
- As a user I can compose and send a new email.
- As a user I can save a composed email as a draft.
- As a user I can edit and send a draft.
- As a user I can reply to an email (creates a child email with `parent_id`).
- As a user I can forward an email.
- As a user I can view the full thread of an email.
- As a user I can download an attachment from an email.
- As a user I can move an email to trash.
- As a user I can permanently delete an email.
- As a user I can restore a trashed email to inbox.
- As a user I can mass-move emails to trash.
- As a user I can mass-delete emails (trash → permanent delete).
- As a user I can tag an email with one or more tags.
- As a user I can filter the inbox by tag.
- As a user I can receive inbound emails via a webhook endpoint.

---

## API Endpoints

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| GET | admin.mail.index | `GET /mail/{route?}` | List emails in folder (DataGrid or HTML) |
| GET | admin.mail.view | `GET /mail/{route}/{id}` | View single email thread |
| POST | admin.mail.store | `POST /mail/create` | Compose & send (or save draft) |
| PUT | admin.mail.update | `PUT /mail/edit/{id}` | Update email / send draft |
| GET | admin.mail.attachment_download | `GET /mail/attachment-download/{id?}` | Download attachment |
| DELETE | admin.mail.delete | `DELETE /mail/{id}` | Move to trash or permanent delete |
| POST | admin.mail.mass_update | `POST /mail/mass-update` | Move multiple to folder |
| POST | admin.mail.mass_delete | `POST /mail/mass-destroy` | Move multiple to trash or delete |
| POST | admin.mail.inbound_parse | `POST /mail/inbound-parse` | Webhook for incoming email (no auth) |

### Mail Tags

| Method | Route name | Path | Description |
|--------|-----------|------|-------------|
| POST | admin.mail.tags.attach | `POST /mail/{id}/tags` | Attach tag to email |
| DELETE | admin.mail.tags.detach | `DELETE /mail/{id}/tags` | Detach tag from email |

---

### Folder Values (SupportedFolderEnum)

| Enum | Value | Description |
|------|-------|-------------|
| INBOX | `inbox` | Received emails |
| DRAFT | `draft` | Saved drafts |
| OUTBOX | `outbox` | Queued to send |
| SENT | `sent` | Successfully sent |
| TRASH | `trash` | Soft-deleted |

The `folders` column on `emails` is a JSON array; an email can be in multiple folders simultaneously (e.g., `["inbox", "sent"]` for a self-sent email).

---

### GET /mail/{route}

- `route` must be one of: `inbox`, `draft`, `outbox`, `sent`, `trash`.
- Permission check: `bouncer()->hasPermission('mail.{route}')`.
- When AJAX: returns DataGrid JSON filtered by `folders LIKE '%"route"%'` and `parent_id IS NULL` (top-level threads only).

### GET /mail/{route}/{id}

- Returns the email with eager-loaded: `emails` (children), `attachments`, `emails.attachments`, `lead`, `lead.person`, `lead.tags`, `lead.source`, `lead.type`, `person`.
- View permission applied to the associated lead: if the lead's `user_id` is not in authorized IDs, `lead_id` is unset from the response.
- For draft folder: returns JSON `{ data: EmailResource }`.
- For other folders: returns HTML view.

### POST /mail/create

**Validation:**
```
reply_to    array   required  min 1 element
reply_to.*  email   each must be valid email
reply       string  required  email body HTML
```

**Additional fields:**
```
subject     string    optional
from        json      optional  sender info
name        string    optional  sender display name
cc          json      optional  array of email addresses
bcc         json      optional
is_draft    boolean   optional  if truthy, saved to draft; else sent
lead_id     int       optional  associate with lead
person_id   int       optional  associate with person
parent_id   int       optional  for replies/threads
attachments file[]   optional
```

**On send:**
1. Creates email record with `folders = ['draft']` initially.
2. Calls `Mail::send(new Email($email))`.
3. On success: updates `folders = ['sent']`.
4. On mail failure: silently caught.

**On draft:**
- Saves with `folders = ['draft']`.

### PUT /mail/edit/{id}

- Applies same folder logic as store.
- If `is_draft = false`: sends the email and sets `folders = ['inbox', 'sent']`.

### DELETE /mail/{id}

Query param `type`:
- `type = 'trash'`: Updates `folders = ['trash']` (soft delete).
- Other: Permanently deletes the record.

### POST /mail/mass-update

```
indices   int[]    required  email IDs
folders   array    required  target folder array
```

### POST /mail/mass-destroy

```
indices   int[]    required  email IDs
type      string   optional  'trash' = move to trash; otherwise permanent delete
```

### POST /mail/inbound-parse

No authentication. Delegates to `InboundEmailProcessor::processMessage(request('email'))`. The inbound parse creates email records from raw incoming email data.

---

## DB Schema

### `emails`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| id | int unsigned | no | auto | PK |
| subject | varchar | yes | | |
| source | varchar | no | | e.g. 'web', 'email' |
| user_type | varchar | no | | |
| name | varchar | yes | | sender display name |
| reply | text | yes | | email body HTML |
| is_read | tinyint | no | 0 | |
| folders | json | yes | | array of folder values |
| from | json | yes | | sender email(s) |
| sender | json | yes | | |
| reply_to | json | yes | | recipient(s) |
| cc | json | yes | | |
| bcc | json | yes | | |
| unique_id | varchar | yes | | unique, used for IMAP sync |
| message_id | varchar | no | | unique, RFC 2822 message ID |
| reference_ids | json | yes | | for threading |
| person_id | int unsigned | yes | | FK → persons ON DELETE SET NULL |
| lead_id | int unsigned | yes | | FK → leads ON DELETE SET NULL |
| parent_id | int unsigned | yes | | FK → emails ON DELETE CASCADE (self-ref) |
| created_at | timestamp | | | |
| updated_at | timestamp | | | |

### `email_attachments`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| id | int unsigned | no | PK |
| name | varchar | yes | |
| path | varchar | no | storage path |
| size | int | yes | bytes |
| content_type | varchar | yes | MIME type |
| content_id | varchar | yes | for inline images |
| email_id | int unsigned | no | FK → emails ON DELETE CASCADE |
| created_at | timestamp | | |
| updated_at | timestamp | | |

### `email_tags`

| Column | Type | Notes |
|--------|------|-------|
| tag_id | int unsigned | FK → tags ON DELETE CASCADE |
| email_id | int unsigned | FK → emails ON DELETE CASCADE |

---

## Filtering & Sorting (DataGrid)

The EmailDataGrid always filters by the current `route` folder (`folders LIKE '%"route"%'`) and `parent_id IS NULL`.

| Index | DB Column | Filter Type |
|-------|-----------|-------------|
| attachments | computed count | not filterable (icon display) |
| name | emails.name | text (searchable, sortable) |
| subject | emails.subject | text (searchable, sortable) |
| reply | emails.reply | text (searchable, sortable) |
| tags | tags.name | searchable_dropdown (TagRepository) |
| created_at | emails.created_at | date_range |

Default sort: `created_at DESC`.

---

## Business Rules

1. **Folder as JSON array:** An email can be in multiple folders simultaneously (e.g., both `inbox` and `sent`).
2. **Thread structure:** Child emails have `parent_id` set to the top-level email. The DataGrid only shows top-level emails (`parent_id IS NULL`).
3. **View permission on leads:** When viewing an email that has `lead_id`, the lead's `user_id` is checked against authorized IDs. If not authorized, the `lead_id` field is stripped from the response.
4. **Inbound parse:** Processes raw email (e.g., from a mail server webhook or IMAP sync) via a dedicated processor class. Creates email records automatically.
5. **Send failure is silent:** If `Mail::send` throws, the exception is caught silently; the email stays in sent folder (optimistic).
6. **Attachment download:** Uses `attachmentRepository->findOrFail($id)` then `Storage::download($attachment->path)`.
7. **Events fired:** `email.create.before/after`, `email.update.before/after`, `email.trash.before/after`, `email.delete.before/after`.

---

## Permissions

| Action | ACL Key |
|--------|---------|
| View inbox | `mail.inbox` |
| View draft | `mail.draft` |
| View outbox | `mail.outbox` |
| View sent | `mail.sent` |
| View trash | `mail.trash` |
| Compose / send | `mail.compose` |
| Quick compose | `mail.compose.quick-create` |
| View single email | `mail.view` |
| Edit / update | `mail.edit` |
| Delete / trash | `mail.delete` |
| Mass delete | `mail.delete` |
