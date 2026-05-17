# CRM Parity Gaps — What to Implement Next

> **Read order:** `07-verification-findings.md` → this doc → `05-database-design.md` → `06-implementation-roadmap.md`.
>
> The verification doc lists everything in current code that's wrong **on its own terms**. This doc lists everything Krayin does that we still don't, plus the items the previous draft of this file got wrong.

Numbered items below are concrete, file-scoped tasks. Each has files to touch and behaviour to verify.

---

## Phase 0 prerequisites — see `07-verification-findings.md`

The work below assumes Phase 0 has been completed:

- Tenant context plumbed through JWT and propagated to `TenantContext`
- Composite unique constraints on per-tenant fields
- `Role.permissionType` (ALL / CUSTOM) added; ADMIN seeded as ALL
- `Tenant` entity + `TenantProvisioningService`
- Permission keys reconciled per `04-permission-model.md`

Without these, any feature added below leaks across tenants the moment a second tenant exists.

---

## Priority 1 — Correctness fixes

### 1.1 Activity types — additive migration (revise previous recommendation)

Current `crm/activity/domain/ActivityType.kt` has `CALL, EMAIL, MEETING, TASK, NOTE, MESSAGE`. Krayin has `call, meeting, lunch, note, file`.

**Previous draft was wrong** — it proposed `enum class ActivityType { CALL, MEETING, LUNCH, NOTE, FILE }`, which would lose any existing `EMAIL`/`TASK`/`MESSAGE` rows.

**Fix:** additive.

```kotlin
enum class ActivityType { CALL, MEETING, LUNCH, NOTE, FILE, TASK, EMAIL, MESSAGE }
//                                          ^^^^^^^^^^^^ added           ^^^^ existing
```

UI hides `TASK`, `EMAIL`, `MESSAGE` from the picker but still renders them when existing records reference them. Migration:

```sql
-- V025__activity_type_additions.sql
ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_type_check;
ALTER TABLE activities ADD CONSTRAINT activities_type_check
    CHECK (type IN ('CALL', 'MEETING', 'LUNCH', 'NOTE', 'FILE', 'TASK', 'EMAIL', 'MESSAGE'));
```

Coupled changes:

- `Activity.kt:55-59` — make `scheduleFrom`/`scheduleTo` nullable (NOTE and FILE don't need them).
- `ActivityService.create()` — auto-set `isDone = true` when `type == NOTE`.
- `ActivityController.create()` — validate schedule fields are required unless type ∈ {NOTE, FILE}.

### 1.2 Activity — missing fields (`location`, `additional`)

Krayin's `activities` has `location: varchar` and `additional: json`. Add:

```kotlin
// crm/activity/domain/Activity.kt
@Column var location: String? = null
@Column(columnDefinition = "jsonb") var additional: String? = null   // stored as JSON string, parsed via Jackson
```

Migration (same V025 file):

```sql
ALTER TABLE activities ADD COLUMN IF NOT EXISTS location VARCHAR(255);
ALTER TABLE activities ADD COLUMN IF NOT EXISTS additional JSONB;
ALTER TABLE activities ALTER COLUMN schedule_from DROP NOT NULL;
ALTER TABLE activities ALTER COLUMN schedule_to   DROP NOT NULL;
```

Update `CreateActivityRequest`, `UpdateActivityRequest`, `ActivityResponse`.

### 1.3 Activity participants — support person participants

Today `activity_participants` is a `Set<UUID>` of user IDs (see `Activity.kt:64-67`). Krayin's `activity_participants` accepts either `user_id` OR `person_id`.

Replace the simple `ElementCollection` with a proper entity:

```kotlin
// crm/activity/domain/ActivityParticipant.kt
@Entity
@Table(name = "activity_participants")
class ActivityParticipant : BaseEntity() {
    @Column(nullable = false) var activityId: UUID = ZERO_UUID
    @Column var userId: UUID? = null
    @Column var personId: UUID? = null
}
```

DB:

```sql
-- V026__activity_participants_revamp.sql
ALTER TABLE activity_participants RENAME COLUMN user_id TO participant_user_id;
ALTER TABLE activity_participants ALTER COLUMN participant_user_id DROP NOT NULL;
ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS person_id UUID REFERENCES persons(id) ON DELETE CASCADE;
ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS id UUID PRIMARY KEY DEFAULT gen_random_uuid();
ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE activity_participants ADD CONSTRAINT chk_participant_type
    CHECK (
        (participant_user_id IS NOT NULL AND person_id IS NULL) OR
        (participant_user_id IS NULL AND person_id IS NOT NULL)
    );
```

Endpoint additions:

```
POST /api/activities/{id}/participants/users   body: { userId }
POST /api/activities/{id}/participants/persons body: { personId }
DELETE /api/activities/{id}/participants/{participantId}
```

### 1.4 Person — multiple emails and phone numbers (JSON arrays)

`crm/contact/domain/Person.kt` has scalar `email` and `phone`. Krayin uses `emails: jsonb`, `contact_numbers: jsonb` (each `[{value, label}]`). Migrate:

```kotlin
@Column(columnDefinition = "jsonb", nullable = false)
var emails: String = "[]"

@Column(columnDefinition = "jsonb", nullable = false)
var contactNumbers: String = "[]"
```

Migration (transitional — keep old columns until cutover):

```sql
-- V027__person_json_contacts.sql
ALTER TABLE persons ADD COLUMN IF NOT EXISTS emails JSONB NOT NULL DEFAULT '[]';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS contact_numbers JSONB NOT NULL DEFAULT '[]';

UPDATE persons
SET emails = jsonb_build_array(jsonb_build_object('value', email, 'label', 'primary'))
WHERE email IS NOT NULL AND emails = '[]'::jsonb;

UPDATE persons
SET contact_numbers = jsonb_build_array(jsonb_build_object('value', phone, 'label', 'primary'))
WHERE phone IS NOT NULL AND contact_numbers = '[]'::jsonb;

-- Drop the scalar columns in a later migration after the read path is fully cut over.
```

DTOs need typed Kotlin data classes (`EmailEntry(value: String, label: String)`), serialized via Jackson, with a custom `AttributeConverter` if you want the entity to expose `List<EmailEntry>` rather than raw JSON string.

### 1.5 Pipeline — `rottenDays` + delete guards

`Pipeline.kt` is missing `rottenDays: Int?`. Also missing:

- Guard against deleting the default pipeline.
- Lead orphaning when a pipeline / stage is deleted — leads must be migrated to the default pipeline's first stage.

```kotlin
@Column var rottenDays: Int? = null
```

```sql
-- V028__pipeline_additions.sql
ALTER TABLE pipelines ADD COLUMN IF NOT EXISTS rotten_days INT;
```

Service changes (`crm/lead/service/PipelineService.kt`):

```kotlin
@Transactional
fun delete(id: UUID) {
    val pipeline = requirePipeline(id)
    if (pipeline.isDefault) throw IllegalStateException("Cannot delete the default pipeline")
    val defaultPipeline = pipelineRepository.findDefault()
        ?: throw IllegalStateException("No default pipeline configured for this tenant")
    val defaultFirstStage = stageRepository.findFirstByPipelineIdOrderBySortOrderAsc(defaultPipeline.id!!)
        ?: throw IllegalStateException("Default pipeline has no stages")
    leadRepository.migratePipelineLeads(id, defaultPipeline.id!!, defaultFirstStage.id!!)
    pipelineRepository.delete(pipeline)
}
```

Same pattern in `Stage` delete — migrate leads to the pipeline's first stage.

### 1.6 Quote — `personId`, addresses, status flow

`Quote.kt` is missing `personId` (Krayin: NOT NULL FK), `billingAddress`, `shippingAddress`, and an enforced status flow.

```kotlin
@Column var personId: UUID? = null   // nullable for now; tighten after migration
@Column(columnDefinition = "jsonb") var billingAddress: String? = null
@Column(columnDefinition = "jsonb") var shippingAddress: String? = null
```

```sql
-- V028__quote_additions.sql (same file as pipeline)
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS person_id        UUID REFERENCES persons(id) ON DELETE SET NULL;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS billing_address  JSONB;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS shipping_address JSONB;
```

After Person migration in 1.4, derive default `person_id` from the linked lead.

### 1.7 Service scoping — close the leaks (P1-1 in verification)

Extract a single `ScopeResolver`:

```kotlin
@Component
class ScopeResolver(private val identityApi: IdentityApi) {
    fun userIdsForCurrentUser(): Set<UUID>? {
        val email = SecurityContextHolder.getContext().authentication?.name ?: return null
        return identityApi.resolveViewContextByEmail(email).userIds
    }
}
```

Use it in:

- `LeadService.search()` — currently unscoped (leak)
- `LeadService.kanban()` — currently unscoped
- `ActivityService.findAll()` / `filter()` — currently unscoped
- `EmailService.findAll()` — currently unscoped; scope by linked lead.userId
- `QuoteService.search()` — verify
- `PersonService` / `OrganizationService` — already scoped via duplicated helper; switch to the shared one

Remove the duplicated `resolveScope()` methods from `LeadService`, `PersonService`, `OrganizationService`, `QuoteService`.

### 1.8 `leads.create` (and friends) — see § P1-2 in verification

Wire new permission keys per `04-permission-model.md`. Files:

- `crm/CrmPermissionRegistry.kt`, `inventory/InventoryPermissionRegistry.kt`, etc. — add `.create` registrations.
- All `*Controller.kt` — change `POST` annotation from `.edit` to `.create`.
- Bootstrap SALESPERSON's allowlist — explicitly include `.create` keys.
- Add the V0NN migration from `04-permission-model.md` § 9 to backfill existing CUSTOM roles.

### 1.9 Business rule guards

| Service | Guard |
|---|---|
| `UserService.deactivate()` | Cannot deactivate self; cannot deactivate the last admin. |
| `UserService.massDeactivate()` | Filter self out; refuse if it would leave zero active admins. |
| `RoleService.delete()` | Refuse if any user has the role; refuse if it's the last ADMIN role. |
| `PersonService.delete()` | Refuse (or move to soft-delete only) if `leadRepository.existsByPersonId(id)`. |
| `MarketingEventService.delete()` | Refuse if any campaign references it. |
| `TagService.delete()` | Refuse if any record references the tag — or detach first, depending on desired UX. |

---

## Priority 2 — Missing endpoints / features

### 2.1 Lead inline person creation

Krayin's "create lead" form lets you type a new person's name + email inline. Add:

```
POST /api/leads
body: {
    title: ..., pipelineId: ..., stageId: ...,
    person: { firstName, lastName, emails: [...], contactNumbers: [...] }   // inline
    // OR personId: ...                                                       // existing
}
```

Service creates the person first (if `person` provided), then the lead.

### 2.2 Rotten-lead computation

After 1.5, expose:

```
GET /api/leads?rottenOnly=true
```

Implementation:

```kotlin
@Query("""
    SELECT l FROM Lead l JOIN Pipeline p ON l.pipelineId = p.id
    WHERE p.rottenDays IS NOT NULL
      AND l.status = 'OPEN'
      AND l.createdAt < :cutoff(:rottenDays)
""")
```

A scheduled job optionally writes a `Lead.isRotten` denormalized flag — only useful if rotten filtering becomes high-volume.

### 2.3 Quote — lead-product sync

When quote items change, sync to `lead_products` for the linked lead. Today they diverge. Sync on:

- Quote item create → ensure `lead_products` row exists with same qty/price
- Quote item update → update `lead_products`
- Quote item delete → don't auto-delete from `lead_products` (could be linked from other quotes)

### 2.4 Quote — search, expired filter

```
GET /api/quotes/search?q=...
GET /api/quotes?expiredOnly=true
```

### 2.5 Dashboard — Krayin parity stats

New `DashboardStatsController.kt` with `GET /api/dashboard/stats?type=...&startDate=...&endDate=...`:

| `type` | Returns |
|---|---|
| `over-all` | counts of leads / activities / quotes / persons for current and previous period, with % change |
| `revenue-stats` | sum of won lead amount vs lost lead amount in period |
| `total-leads` | time-series of lead create count, bucketed by day/week/month |
| `revenue-by-sources` | sum of won amount grouped by `lead_source_id` |
| `revenue-by-types` | grouped by `lead_type_id` |
| `top-selling-products` | sum of `lead_products.quantity * unit_price` grouped by `product_id`, top 10 |
| `top-persons` | sum of won amount grouped by `person_id`, top 10 |
| `open-leads-by-states` | count of OPEN leads grouped by `stage_id` |

All apply view scoping (`ScopeResolver`).

### 2.6 Web form public submission

Public endpoint (already allow-listed in `SecurityConfig`):

```
POST /api/web-forms/{formId}/submit   body: { attributeValues: {...} }
```

Creates a person from form attributes; optionally creates a lead per the form's config; honours rate limiting; returns success or redirect URL.

### 2.7 Mail — drafts, forward, per-folder permissions

After permission key reconciliation:

- `POST /api/mail` accepts `isDraft: true` → status starts as DRAFT, not SENT.
- `POST /api/mail/{id}/send` to send a draft.
- `POST /api/mail/{id}/forward` to forward.
- Each folder listing endpoint checks `mail.<folder>` permission.

### 2.8 Activity — calendar view, meeting overlap

```
GET /api/activities/calendar?start=&end=          -- date-range query
POST /api/activities/check-overlap                -- validate proposed schedule against current participants
```

### 2.9 Attachments on email compose

`POST /api/mail` should accept `multipart/form-data` with attachments, or take `attachmentIds: UUID[]` referring to pre-uploaded files. Today neither is supported.

### 2.10 Workflow engine — action types beyond LOG

Today only `LOG` is implemented. Add Krayin's set (see `krayin-features-analysis/11-settings-automation.md`):

- `update_lead` — set field on the triggering lead
- `update_person` — set field on linked person
- `send_email_to_person` (uses email template)
- `send_email_to_sales_owner`
- `add_tag`
- `add_note_as_activity`
- `trigger_webhook`

Each action is a `WorkflowAction` strategy registered in a `Map<String, WorkflowAction>`. The condition evaluator (`AND`/`OR` of `(attribute, operator, value)`) is independent and can be tested in isolation.

---

## Priority 3 — Polish

### 3.1 Auto soft-delete via `@SQLDelete` / `@Where`

For every entity that implements `SoftDeletable`, add Hibernate annotations so deletes never accidentally become hard deletes and queries never accidentally return soft-deleted rows.

```kotlin
@Entity
@Table(name = "leads")
@SQLDelete(sql = "UPDATE leads SET deleted_at = NOW() WHERE id = ? AND version = ?")
@Where(clause = "deleted_at IS NULL")
class Lead : AuditableEntity(), SoftDeletable { ... }
```

Caveat: `@Where` is bypassed by native queries — keep the explicit `deleted_at IS NULL` clause in any `@Query(nativeQuery = true)`.

### 3.2 Extend soft delete to entities that don't have it

These should also be soft-deletable: `Email`, `EmailAttachment`, `EmailTemplate`, `Attribute`, `WebForm`, `Workflow`, `Webhook`, `MarketingCampaign`, `MarketingEvent`. Add column + interface implementation.

### 3.3 Duplicate `UserPrincipal` cleanup

Delete one of `auth/UserPrincipal.kt` and `auth/config/UserPrincipal.kt`. Whichever isn't used by `JwtAuthFilter` and `JpaAuditingConfig`.

### 3.4 Consolidate scope helpers

After 1.7's `ScopeResolver`, no service should reach into `SecurityContextHolder` directly for scoping. Audit.

### 3.5 Module port leaks

Anywhere CRM code imports `inventory.*.domain.*` or `inventory.*.repo.*` directly, route the call through `InventoryApi` instead. Same for any cross-module access. `ModularityTests.kt` should grow assertions for this.
