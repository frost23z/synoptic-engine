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
        TenantContext.clear()
    }
}
