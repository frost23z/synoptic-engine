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
 * Runs `SET LOCAL app.current_tenant = '<tenantId>'` as the first statement of
 * every Spring `@Transactional` method, when `TenantContext` is populated.
 *
 * This is the runtime half of `V007__sharing_and_rls.sql`: the policies reference
 * `app_current_tenant()` (a stable function reading
 * `current_setting('app.current_tenant', true)`). Without the GUC being set,
 * the first clause of every policy (`app_current_tenant() IS NULL OR …`)
 * bypasses RLS entirely.
 *
 * **Aspect ordering.** Spring's `@Transactional` AOP advice defaults to
 * [Ordered.LOWEST_PRECEDENCE] — which is INNERMOST. For our SET LOCAL to land
 * *inside* the transaction this aspect needs to be even more inner, and Spring
 * caps advisor order at `Integer.MAX_VALUE`. The fix is to flip Spring's
 * `@Transactional` to high precedence with `@EnableTransactionManagement(order = 0)`
 * on `Application`, then leave this aspect at a modest `@Order(100)`. With that
 * ordering, `@Transactional` opens the transaction, then this aspect's
 * `@Around` advice fires inside it.
 *
 * **Why not `TransactionExecutionListener`?** Spring Framework 7 ships the
 * listener interface and `AbstractPlatformTransactionManager` supports
 * `addListener(...)`, but as of Spring Boot 4.0.6 the call path that the
 * default `JpaTransactionManager` takes for read-write transactions doesn't
 * always invoke `afterBegin` on registered listeners. Rather than rely on
 * an implementation detail of the TM, the `@Around` aspect is unambiguous —
 * it runs once we're inside Spring's transaction proxy.
 *
 * **Tests.** Testcontainers' default Postgres user is superuser
 * (`BYPASSRLS = true`), so RLS policies don't filter rows in the test suite —
 * the Hibernate `@Filter` continues to be the runtime isolation in tests.
 * This wiring's job is to make production's `synoptic_app` (BYPASSRLS = false)
 * role see the policies fire. `RlsTenantGucIntegrationTest` proves the GUC
 * *is* set by reading it back inside the same transaction.
 *
 * **Null tenant context.** Transactions opened without `TenantContext` set —
 * bootstrap, public endpoints (login, web-form submission, inbound mail
 * parse) — skip the SET LOCAL. The policies then evaluate
 * `app_current_tenant() IS NULL` and bypass; the application enforces tenant
 * boundary through other means in those paths (`BaseEntity.@PrePersist` for
 * writes; explicit `TenantContext.runAs(...)` for reads).
 */
@Aspect
@Component
// Higher numerical order = lower precedence = INNER advice. We want to run
// inside Spring's @Transactional (which `@EnableTransactionManagement(order = 0)`
// fixes at the outermost slot).
@Order(100)
class RlsTenantGucAspect(
    private val entityManager: EntityManager,
) {
    @Around(
        "@annotation(org.springframework.transaction.annotation.Transactional) " +
            "|| @within(org.springframework.transaction.annotation.Transactional)",
    )
    fun aroundTransactional(joinPoint: ProceedingJoinPoint): Any? {
        val tenantId = TenantContext.get()
        if (tenantId != null) {
            // Issue SET LOCAL through the raw JDBC connection rather than
            // `entityManager.createNativeQuery(...).executeUpdate()`. Hibernate
            // auto-flushes the persistence context before running a write query,
            // and if this aspect fires for a `@Transactional` method invoked from
            // inside an already-running Hibernate flush (e.g. the cross-tenant
            // audit listener that reacts to `Interceptor.onFlushDirty`), that
            // nested flush re-fires `onFlushDirty` on the same dirty entity, and
            // the listener publishes the event again — infinite recursion that
            // dies with StackOverflowError. `doWork` operates on the JDBC
            // connection directly and skips JPA's flush plumbing.
            //
            // SET LOCAL doesn't accept bind parameters in PostgreSQL (parser
            // limitation). Inlining is safe because the value is a UUID — its
            // `toString()` cannot contain SQL metacharacters.
            entityManager.unwrap(Session::class.java).doWork { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("SET LOCAL app.current_tenant = '$tenantId'")
                }
            }
        }
        return joinPoint.proceed()
    }
}
