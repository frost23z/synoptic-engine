# Database Design — Changes Needed

## Current Migration State

Latest migration: `V024__create_datagrid_saved_filters.sql`

All tables already have:
- UUID primary keys
- `tenant_id UUID` for multi-tenant isolation
- Hibernate `@Filter(name = "tenantFilter")` applied automatically
- `deleted_at TIMESTAMPTZ` for soft deletes on core entities
- `version BIGINT` for optimistic locking (V020)

---

## Migrations to Add

### V025 — Activity Missing Fields

```sql
-- V025__activity_missing_fields.sql
ALTER TABLE activities ADD COLUMN IF NOT EXISTS location VARCHAR(255);
ALTER TABLE activities ADD COLUMN IF NOT EXISTS additional JSONB;
ALTER TABLE activities ALTER COLUMN schedule_from DROP NOT NULL;
ALTER TABLE activities ALTER COLUMN schedule_to DROP NOT NULL;

-- Change ActivityType check constraint (if exists)
-- Drop old and add new
ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_type_check;
ALTER TABLE activities ADD CONSTRAINT activities_type_check 
    CHECK (type IN ('CALL', 'MEETING', 'LUNCH', 'NOTE', 'FILE'));
```

### V026 — Activity Participant Persons

```sql
-- V026__activity_participant_persons.sql

-- The current activity_participants table only has user_id.
-- Extend to support both user and person participants.
ALTER TABLE activity_participants 
    ADD COLUMN IF NOT EXISTS person_id UUID REFERENCES persons(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS participant_type VARCHAR(20) NOT NULL DEFAULT 'user';

-- Rename existing column to make it explicit
ALTER TABLE activity_participants RENAME COLUMN user_id TO participant_user_id;

-- Now participant_user_id can be NULL (when participant_type = 'person')
ALTER TABLE activity_participants ALTER COLUMN participant_user_id DROP NOT NULL;

-- Constraint: exactly one of user_id or person_id must be set
ALTER TABLE activity_participants ADD CONSTRAINT chk_activity_participant_type
    CHECK (
        (participant_type = 'user' AND participant_user_id IS NOT NULL AND person_id IS NULL) OR
        (participant_type = 'person' AND person_id IS NOT NULL AND participant_user_id IS NULL)
    );
```

### V027 — Person JSON Contacts

```sql
-- V027__person_json_contacts.sql

-- Add new JSONB columns for multiple emails and phone numbers
ALTER TABLE persons ADD COLUMN IF NOT EXISTS emails JSONB NOT NULL DEFAULT '[]';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS contact_numbers JSONB NOT NULL DEFAULT '[]';

-- Migrate existing single email/phone to JSON arrays
UPDATE persons 
SET emails = jsonb_build_array(
    jsonb_build_object('value', email, 'label', 'primary')
)
WHERE email IS NOT NULL AND emails = '[]';

UPDATE persons
SET contact_numbers = jsonb_build_array(
    jsonb_build_object('value', phone, 'label', 'primary')
)
WHERE phone IS NOT NULL AND contact_numbers = '[]';

-- Drop old columns (after data migration confirmed)
-- Run this in a subsequent migration after verifying
-- ALTER TABLE persons DROP COLUMN email;
-- ALTER TABLE persons DROP COLUMN phone;
```

**Note:** Keep old `email` and `phone` columns in a transitional migration so rollback is possible. Drop them in V028 after verifying.

### V028 — Pipeline Rotten Days and Quote Addresses

```sql
-- V028__pipeline_quote_additions.sql

-- Pipeline rotten days
ALTER TABLE pipelines ADD COLUMN IF NOT EXISTS rotten_days INT;

-- Quote addresses and missing fields  
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS billing_address JSONB;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS shipping_address JSONB;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS person_id UUID REFERENCES persons(id) ON DELETE SET NULL;

