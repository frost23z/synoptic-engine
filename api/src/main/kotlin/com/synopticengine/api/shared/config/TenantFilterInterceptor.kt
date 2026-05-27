package com.synopticengine.api.shared.config

import com.synopticengine.api.shared.TenantContext
import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.hibernate.Session
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Enables the Hibernate tenant filter for the duration of an HTTP request — only when
 * [TenantContext] is set. Public endpoints (login, web-form submission) run without a
 * tenant in scope; their handlers are responsible for not leaking cross-tenant writes
 * (the [com.synopticengine.api.shared.domain.BaseEntity] @PrePersist hook is the
 * authoritative gate).
 *
 * **OSIV coupling.** The app relies on `spring.jpa.open-in-view` (Open-In-View is
 * enabled by default in Spring Boot). The OSIV interceptor opens one Hibernate
 * `Session` per HTTP request before `preHandle` fires and closes it after the
 * response is committed. That means [enableFilter] in `preHandle` and
 * [disableFilter] in `afterCompletion` both operate on the **same** session
 * instance. If OSIV is ever disabled (`spring.jpa.open-in-view=false`), each
 * `@Transactional` boundary would start/close its own session — the filter enable
 * in `preHandle` would touch a session that is immediately discarded, and the
 * [HibernateTenantFilterAspect] (which fires inside `@Transactional`) would become
 * the sole enablement mechanism for MVC paths too. No code change is required; just
 * re-test isolation after removing OSIV.
 */
@Component
class TenantFilterInterceptor(
    private val entityManager: EntityManager,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val tenantId = TenantContext.get() ?: return true
        val session = entityManager.unwrap(Session::class.java)
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId)
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        // T2.6 — disable the filter so the session (kept alive by OSIV until response
        // commit) cannot be accidentally reused for cross-tenant reads after the
        // request logic has completed. This is belt-and-braces: the OSIV session is
        // discarded after this hook, so leaked session reuse is not practically
        // possible today. Disabling eagerly is cheap and makes intent explicit.
        try {
            entityManager.unwrap(Session::class.java).disableFilter("tenantFilter")
        } catch (_: Exception) {
            // Session may already be closed (e.g. error before OSIV bind) — ignore.
        }
        TenantContext.clear()
    }
}
