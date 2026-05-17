package com.synopticengine.api.identity

import java.util.UUID

/**
 * Published after a tenant is created and identity-owned defaults are seeded (roles,
 * admin user). Other modules listen synchronously to seed their own defaults: CRM seeds
 * the default pipeline, lead sources, and lead types; future modules add their own.
 *
 * The event is fired inside a [com.synopticengine.api.shared.TenantContext.runAs] block,
 * so listeners can call repositories normally and writes will carry the right tenant id.
 */
data class TenantProvisionedEvent(
    val tenantId: UUID,
    val tenantSlug: String,
)
