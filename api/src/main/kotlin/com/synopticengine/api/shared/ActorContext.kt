package com.synopticengine.api.shared

import java.util.UUID

/**
 * Per-thread identifier of the authenticated user. Populated by
 * [com.synopticengine.api.auth.config.JwtAuthFilter] for HTTP requests, by [runAs]
 * for system code paths (bootstrap, tenant provisioning, async tasks that need a
 * synthetic actor).
 *
 * Code that needs the acting user — e.g. the cross-tenant audit log — reads from
 * here. Sibling of [TenantContext]; both are cleared by [JwtAuthFilter] in `finally`.
 */
object ActorContext {
    private val holder = ThreadLocal<UUID?>()

    fun set(userId: UUID) = holder.set(userId)

    fun get(): UUID? = holder.get()

    inline fun <T> runAs(
        userId: UUID,
        block: () -> T,
    ): T {
        val previous = get()
        set(userId)
        try {
            return block()
        } finally {
            if (previous == null) clear() else set(previous)
        }
    }

    fun clear() = holder.remove()
}
