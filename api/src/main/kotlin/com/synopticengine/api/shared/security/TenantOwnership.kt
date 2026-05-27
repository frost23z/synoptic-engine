package com.synopticengine.api.shared.security

import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.domain.BaseEntity

/**
 * Defense-in-depth tenant-ownership assertion.
 *
 * Hibernate's `@Filter("tenantFilter")` already rewrites every JPQL/Criteria
 * query so that cross-tenant rows are invisible, and Postgres RLS adds a third
 * layer at the DB level. This guard is a **fourth** layer: an explicit equality
 * check in application code that fires even if a bug in one of the lower layers
 * lets a cross-tenant row slip through.
 *
 * Calling convention:
 * ```kotlin
 * val entity = repository.findActiveById(id)
 *     ?: throw NoSuchElementException("Not found: $id")
 * entity.requireOwnership()          // throws 403 if tenant mismatch
 * return entity.toResponse()
 * ```
 *
 * The method **throws [org.springframework.security.access.AccessDeniedException]**
 * (→ HTTP 403) rather than `NoSuchElementException` (→ HTTP 404) because a tenant
 * mismatch on a loaded entity means something bypassed the JPQL filter — returning
 * 404 would silently mask the anomaly. A 403 is more informative for debugging while
 * still not leaking row details.
 */
fun BaseEntity.requireOwnership() {
    val current =
        TenantContext.get()
            ?: return // Public endpoints (login, inbound-mail) run without a tenant context —
    // those paths should not hold tenant-scoped entities, but we can't
    // enforce that here without breaking them. Skip the check.
    if (tenantId != current) {
        throw org.springframework.security.access.AccessDeniedException(
            "Access denied: entity ${this::class.simpleName}/$id belongs to a different tenant",
        )
    }
}
