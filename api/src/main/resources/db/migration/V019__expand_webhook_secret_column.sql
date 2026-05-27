-- V019 — Expand webhooks.secret to accommodate AES-GCM encrypted values (T2.4).
--
-- The original column (V017) is varchar(255).  An AES-GCM encrypted value has the form:
--
--   ENC:<base64( 12-byte IV || ciphertext || 16-byte GCM tag )>
--
-- For a 200-character plaintext secret the resulting stored value is ~308 characters,
-- which exceeds varchar(255).  Expanding to TEXT removes the upper bound entirely and
-- is consistent with how system_configs.value is typed.

ALTER TABLE webhooks
    ALTER COLUMN secret TYPE TEXT;
