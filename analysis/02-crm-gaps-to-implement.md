# CRM Parity Gaps — What to Implement Next

This document lists concrete implementation tasks ordered by priority. Each item includes the files to touch and the exact behavior needed.

---

## Priority 1 — Critical Correctness Fixes

These are behavioral gaps that make the current API incorrect or misleading.

### 1.1 Activity Types — Align with Krayin

**Problem:** `ActivityType` enum currently has `TASK` as a type. Krayin uses: `call`, `meeting`, `lunch`, `note`, `file`.

**Fix:**
```kotlin
// ActivityType.kt
enum class ActivityType { CALL, MEETING, LUNCH, NOTE, FILE }
```

Also update:
- `Activity.kt` — remove `scheduleFrom`/`scheduleTo` as `nullable = false`; make them nullable (notes and files don't need them)
- `ActivityService.create()` — auto-set `isDone = true` when `type == NOTE`
- `ActivityController` — validate: schedule fields required unless type is NOTE or FILE
- Migration: update the enum/check constraint in the DB

### 1.2 Activity — Missing Fields

**Problem:** `Activity` entity is missing `location` (String?) and `additional` (JSONB).

**Fix:** Add columns to entity and migration:
```kotlin
@Column
var location: String? = null

@Column(columnDefinition = "JSONB")
var additional: String? = null  // store as JSON string
```

New migration `V025__activity_missing_fields.sql`:
```sql
ALTER TABLE activities ADD COLUMN IF NOT EXISTS location VARCHAR(255);
ALTER TABLE activities ADD COLUMN IF NOT EXISTS additional JSONB;
```

Update `CreateActivityRequest`, `UpdateActivityRequest`, and `ActivityResponse` DTOs.

### 1.3 Activity Participants — Add Person Participants

**Problem:** Participants are stored as `Set<UUID>` in `activity_participants.user_id`. Krayin also supports person participants (person_id).

**Fix:**
- Change `activity_participants` table to have a discriminated structure:
  ```sql
  -- V026__activity_participant_persons.sql
  ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS person_id UUID;
  ALTER TABLE activity_participants ADD COLUMN IF NOT EXISTS participant_type VARCHAR(20) NOT NULL DEFAULT 'user';
  ```
- Update `Activity.kt` — replace single `participantIds: Set<UUID>` with a proper `ActivityParticipant` embedded entity or separate collection
- Add `AddPersonParticipantRequest` and endpoint: `POST /activities/{id}/person-participants`

### 1.4 Person — Multiple Emails and Phone Numbers

**Problem:** `Person` entity stores single `email: String?` and `phone: String?`. Krayin stores JSON arrays like `[{"value":"...","label":"work"}]`.

**Fix:**
```kotlin
// Person.kt
@Column(columnDefinition = "JSONB")
var emails: String = "[]"  // JSON: [{value, label}]

@Column(columnDefinition = "JSONB")
var contactNumbers: String = "[]"
```

Migration:
```sql
-- V027__person_json_contacts.sql
ALTER TABLE persons ADD COLUMN IF NOT EXISTS emails JSONB NOT NULL DEFAULT '[]';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS contact_numbers JSONB NOT NULL DEFAULT '[]';
-- Migrate existing data
UPDATE persons SET emails = jsonb_build_array(jsonb_build_object('value', email, 'label', 'primary')) WHERE email IS NOT NULL;
ALTER TABLE persons DROP COLUMN email;
ALTER TABLE persons DROP COLUMN phone;
```

Update all DTOs and services.

### 1.5 Pipeline — Rotten Days and Guard Rules

**Problem:** `Pipeline` entity is missing `rottenDays: Int?`. Also, there are no guards preventing:
1. Deletion of the default pipeline
2. Lead orphaning when pipeline/stage is deleted

**Fix:**

Add to `Pipeline.kt`:
```kotlin
@Column
var rottenDays: Int? = null

