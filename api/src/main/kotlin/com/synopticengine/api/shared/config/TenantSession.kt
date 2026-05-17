package com.synopticengine.api.shared.config

import com.synopticengine.api.shared.TenantContext
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.springframework.stereotype.Component

/**
 * Applies the Hibernate `tenantFilter` to the current Hibernate session from the
 * [TenantContext]. Used by code paths that don't go through the MVC layer (bootstrap,
 * tenant provisioning, scheduled jobs, tests). MVC requests are already covered by
 * [TenantFilterInterceptor].
 *
 * Idempotent and safe to call repeatedly; a no-op when [TenantContext] is unset.
 */
@Component
class TenantSession(
    private val entityManager: EntityManager,
) {
    fun applyFilter() {
        val tenantId = TenantContext.get() ?: return
        val session = entityManager.unwrap(Session::class.java)
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId)
    }
}
