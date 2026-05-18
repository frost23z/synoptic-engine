-- Phase 1b / P1.3: support person participants on activities.
-- Krayin's activity_participants stores either a user_id or a person_id per row.
-- The original table was an @ElementCollection (composite PK activity_id + user_id)
-- with no version, tenant, or surrogate id; promote it to a first-class entity table.
--
-- Migration steps:
--   1. Drop the composite primary key so we can add a surrogate id.
--   2. Rename user_id -> participant_user_id so the column reflects the new exclusive-or semantics.
--   3. Make participant_user_id nullable; add person_id (nullable FK to persons).
--   4. Add id (PK), tenant_id, version columns to match BaseEntity expectations.
--   5. Add a check constraint enforcing exactly one of (participant_user_id, person_id).

ALTER TABLE activity_participants DROP CONSTRAINT IF EXISTS pk_activity_participants;

ALTER TABLE activity_participants RENAME COLUMN user_id TO participant_user_id;
ALTER TABLE activity_participants ALTER COLUMN participant_user_id DROP NOT NULL;

ALTER TABLE activity_participants
    ADD COLUMN IF NOT EXISTS id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS person_id  UUID         REFERENCES persons (id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS tenant_id  UUID         NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES tenants (id),
    ADD COLUMN IF NOT EXISTS version    BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW();

-- Backfill tenant_id from the parent activity so existing rows land in the correct tenant.
UPDATE activity_participants ap
SET    tenant_id = a.tenant_id
FROM   activities a
WHERE  a.id = ap.activity_id
  AND  ap.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND  a.tenant_id  <> '00000000-0000-0000-0000-000000000001';

ALTER TABLE activity_participants ADD CONSTRAINT pk_activity_participants PRIMARY KEY (id);

ALTER TABLE activity_participants
    ADD CONSTRAINT chk_activity_participants_one_target CHECK (
        (participant_user_id IS NOT NULL AND person_id IS NULL) OR
        (participant_user_id IS NULL     AND person_id IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS idx_activity_participants_activity ON activity_participants (activity_id);
CREATE INDEX IF NOT EXISTS idx_activity_participants_person   ON activity_participants (person_id);
CREATE INDEX IF NOT EXISTS idx_activity_participants_tenant   ON activity_participants (tenant_id);
