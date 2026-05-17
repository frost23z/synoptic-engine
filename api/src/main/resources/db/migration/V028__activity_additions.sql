-- Phase 1 / P1.1, P1.2: align activities with Krayin in an additive, data-safe way.
-- Adds LUNCH and FILE to the type enum (keeping TASK, EMAIL, MESSAGE for backwards
-- compatibility), adds the location + additional fields Krayin has, and makes the
-- schedule fields nullable so NOTE and FILE activities don't need a fake schedule.

ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_type_check;
ALTER TABLE activities ADD CONSTRAINT activities_type_check
    CHECK (type IN ('CALL', 'MEETING', 'LUNCH', 'NOTE', 'FILE', 'TASK', 'EMAIL', 'MESSAGE'));

ALTER TABLE activities ADD COLUMN IF NOT EXISTS location   VARCHAR(255);
ALTER TABLE activities ADD COLUMN IF NOT EXISTS additional JSONB;
ALTER TABLE activities ALTER COLUMN schedule_from DROP NOT NULL;
ALTER TABLE activities ALTER COLUMN schedule_to   DROP NOT NULL;
