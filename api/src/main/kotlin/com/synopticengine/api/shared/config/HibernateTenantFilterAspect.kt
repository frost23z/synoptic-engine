package com.synopticengine.api.shared.config

import com.synopticengine.api.shared.TenantContext
import jakarta.persistence.EntityManager
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.hibernate.Session
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Ensures the Hibernate `tenantFilter` is enabled on the current session whenever
 * [TenantContext] is populated and a Spring `@Transactional` boundary is entered.
 *
 * **Why this is needed.**  [TenantFilterInterceptor] enables the filter for the
 * duration of HTTP requests via Spring MVC's `HandlerInterceptor` hook.  Async
 * entry points (`WorkflowEngine`, `WebhookDispatcher`) run on a thread-pool thread
 * and therefore never pass through that interceptor.  Without this aspect their
 * JPQL queries run without the filter, relying solely on Postgres RLS for tenant
 * isolation.  Adding the Hibernate filter as a second, independently-tested layer
 * closes the test-fidelity gap (integration tests run as BYPASSRLS in the dev DB
 * so only the Hibernate filter catches cross-tenant leaks in tests).
 *
 * **Aspect ordering.** `@EnableTransactionManagement(order = 0)` on [Application]
 * makes `@Transactional` the **outermost** advice.  [RlsTenantGucAspect] sits at
 * `@Order(100)` to fire **inside** the transaction.  This aspect is at `@Order(200)`
 * so it fires after the GUC is set — the ordering is:
 *
 *   ```
 *   @Transactional (order 0)    — opens the Hibernate session / DB transaction
 *     RlsTenantGucAspect (100)  — SET LOCAL app.current_tenant = '<id>'
 *       HibernateTenantFilterAspect (200)  — enableFilter("tenantFilter", tenantId)
 *         [actual method body]
 *   ```
 *
 * **HTTP requests.** The filter is already enabled by [TenantFilterInterceptor] at
 * the start of every authenticated request.  Re-enabling an already-enabled filter
 * is a no-op (checked via `isFilterEnabled`), so the redundant call is harmless.
 *
 * **Public endpoints.** When `TenantContext` is null (login, web-form submission,
 * inbound-mail parse) this aspect returns immediately without touching the session.
 *
 * T2.1 / T7.5 — async-isolation second layer + test backing.
 */
@Aspect
@Component
@Order(200)
class HibernateTenantFilterAspect(
    private val entityManager: EntityManager,
) {
    @Around(
        "@annotation(org.springframework.transaction.annotation.Transactional) " +
            "|| @within(org.springframework.transaction.annotation.Transactional)",
    )
    fun applyTenantFilter(joinPoint: ProceedingJoinPoint): Any? {
        val tenantId = TenantContext.get()
        if (tenantId != null) {
            val session = entityManager.unwrap(Session::class.java)
            // getEnabledFilter returns null if the filter is not currently enabled.
            if (session.getEnabledFilter("tenantFilter") == null) {
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId)
            }
        }
        return joinPoint.proceed()
    }
}
