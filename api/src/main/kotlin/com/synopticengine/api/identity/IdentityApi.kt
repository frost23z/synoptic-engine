package com.synopticengine.api.identity

import java.util.UUID

data class UserSummary(
    val id: UUID,
    val email: String,
    val fullName: String,
    val isActive: Boolean,
)

data class UserCredentials(
    val id: UUID,
    val email: String,
    val fullName: String,
    val passwordHash: String,
    val isActive: Boolean,
    val deletedAt: java.time.Instant?,
    val authorities: List<String>,
)

/**
 * Resolved from the authenticated user's viewPermission.
 * null userIds = unrestricted (ALL / GLOBAL).
 * non-null userIds = restrict list queries to these owner IDs.
 */
data class ViewContext(
    val userIds: Set<UUID>?,
)

interface IdentityApi {
    fun findById(id: UUID): UserSummary?

    fun findAllActive(): List<UserSummary>

    fun existsById(id: UUID): Boolean

    fun findCredentialsByEmail(email: String): UserCredentials?

    fun findCredentialsById(id: UUID): UserCredentials?

    fun resolveViewContext(requesterId: UUID): ViewContext

    /** Convenience: resolve from authenticated email (avoids cross-module UserPrincipal import). */
    fun resolveViewContextByEmail(email: String): ViewContext

    fun updatePassword(
        email: String,
        encodedPassword: String,
    )
}
