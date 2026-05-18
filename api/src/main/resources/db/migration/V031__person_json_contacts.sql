-- Phase 1b / P1.4: multi-value emails and phone numbers on Person, matching Krayin.
-- Krayin stores `emails` and `contact_numbers` as JSONB arrays of {value, label} entries.
-- We add the new columns, backfill from the existing scalar `email` / `phone`, and keep
-- the scalars transitionally so the existing read path doesn't break. A follow-up
-- migration can drop the scalar columns once every consumer reads from the arrays.

ALTER TABLE persons ADD COLUMN IF NOT EXISTS emails          JSONB NOT NULL DEFAULT '[]';
ALTER TABLE persons ADD COLUMN IF NOT EXISTS contact_numbers JSONB NOT NULL DEFAULT '[]';

UPDATE persons
SET    emails = jsonb_build_array(jsonb_build_object('value', email, 'label', 'primary'))
WHERE  email IS NOT NULL
  AND  emails = '[]'::jsonb;

UPDATE persons
SET    contact_numbers = jsonb_build_array(jsonb_build_object('value', phone, 'label', 'primary'))
WHERE  phone IS NOT NULL
  AND  contact_numbers = '[]'::jsonb;
