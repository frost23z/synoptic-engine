package com.synopticengine.api.shared

import java.util.UUID

object TenantContext {
    val SEED_TENANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val holder = ThreadLocal<UUID?>()

    fun set(tenantId: UUID) = holder.set(tenantId)

    fun get(): UUID? = holder.get()

    fun getOrDefault(): UUID = holder.get() ?: SEED_TENANT_ID

    fun clear() = holder.remove()
}
