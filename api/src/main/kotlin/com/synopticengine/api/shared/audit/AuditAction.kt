package com.synopticengine.api.shared.audit

/** Actions logged in the [AuditLog] table for sensitive same-tenant mutations. */
enum class AuditAction {
    CREATE,
    UPDATE,
    DELETE,
}
