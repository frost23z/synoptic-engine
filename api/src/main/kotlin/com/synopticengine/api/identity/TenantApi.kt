package com.synopticengine.api.identity

import java.util.UUID

/**
 * Exposed identity API for provisioning and looking up tenants.
 */
interface TenantApi {
    /**
     * Create a new tenant and seed its defaults (roles, default pipeline, lead sources/types,
     * admin user). Idempotency: a duplicate slug throws.
     */
    fun provision(
        name: String,
        slug: String,
        adminEmail: String,
        adminPassword: String,
    ): TenantSummary

    /**
     * Idempotent. Re-runs seeding for an existing tenant (e.g. the seed tenant on app boot,
     * or after adding a new permission family that ADMIN should pick up automatically).
     */
    fun seedTenantDefaults(
        tenantId: UUID,
        adminEmail: String? = null,
        adminPassword: String? = null,
    )

    /** Lightweight existence check used by cross-module callers (e.g. sharing). */
    fun exists(tenantId: UUID): Boolean

    /** Summary lookup; null if the tenant doesn't exist. */
    fun findSummary(tenantId: UUID): TenantSummary?
}

data class TenantSummary(
    val id: UUID,
    val name: String,
    val slug: String,
    val status: String,
)
