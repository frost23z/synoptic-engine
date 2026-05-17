-- Rename the permissions name column to key (clearer intent: this is a unique dotted key, not a display name)
ALTER TABLE permissions RENAME COLUMN name TO key;
ALTER TABLE permissions RENAME CONSTRAINT uq_permissions_name TO uq_permissions_key;

-- Clear any stale permission data so BootstrapService re-seeds with the new key format on next start.
-- role_permissions has ON DELETE CASCADE so deleting permissions cleans it up automatically.
DELETE FROM permissions;
