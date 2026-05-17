-- Phase 1 / P1.6: bring Quote closer to Krayin parity.

ALTER TABLE quotes ADD COLUMN IF NOT EXISTS person_id        UUID REFERENCES persons(id) ON DELETE SET NULL;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS billing_address  JSONB;
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS shipping_address JSONB;

-- Backfill: copy person_id from the linked lead where possible.
UPDATE quotes q
SET person_id = l.person_id
FROM leads l
WHERE q.lead_id = l.id
  AND q.person_id IS NULL
  AND l.person_id IS NOT NULL;
