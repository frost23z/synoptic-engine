package com.synopticengine.api.shared

import java.util.UUID

/**
 * Per-thread tenant identity. Populated by [com.synopticengine.api.auth.config.JwtAuthFilter]
 * for authenticated HTTP requests, by [runAs] for system code paths (bootstrap, test setup,
 * tenant provisioning).
 *
 * Code that creates new entities must run inside a populated context. Code that only reads
 * may run outside one — the Hibernate tenant filter is simply not applied in that case
 * (intended for public endpoints like login that need to look up users across tenants).
 */
object TenantContext {
    val SEED_TENANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val holder = ThreadLocal<UUID?>()

    fun set(tenantId: UUID) = holder.set(tenantId)

    fun get(): UUID? = holder.get()

    /**
     * Run [block] with [tenantId] in scope, restoring the previous value (often null) after.
     * Use for bootstrap, tenant provisioning, scheduled jobs, and test setup — anywhere code
     * runs outside an authenticated HTTP request but still creates or reads tenant-scoped data.
     */
    inline fun <T> runAs(
        tenantId: UUID,
        block: () -> T,
    ): T {
        val previous = get()
        set(tenantId)
        try {
            return block()
        } finally {
            if (previous == null) clear() else set(previous)
        }
    }

    fun clear() = holder.remove()
}
