-- V024__login_history.sql
-- Records each successful authentication for security audit and
-- "where has my account logged in from" UX.
--
-- Intentionally NOT a BaseEntity table: login() runs before TenantContext
-- is populated (the JWT hasn't been issued yet), so @PrePersist can't fill
-- tenant_id from context. tenant_id is set explicitly from UserCredentials.
--
-- Retention: soft-delete is not needed; hard-delete is handled by a future
-- retention worker (same pattern as audit_logs).

CREATE TABLE login_history (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id    UUID         NOT NULL,
    client_ip    VARCHAR(45),
    logged_in_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_history_user ON login_history (user_id, logged_in_at DESC);
