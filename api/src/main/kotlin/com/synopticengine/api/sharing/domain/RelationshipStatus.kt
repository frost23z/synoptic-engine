package com.synopticengine.api.sharing.domain

/**
 * Lifecycle of a [TenantRelationship].
 *
 * - PENDING — source initiated, target has not accepted
 * - ACTIVE  — both sides agreed; policies and shares may apply
 * - SUSPENDED — temporarily disabled by either side; reversible
 * - REVOKED — terminal; share visibility removed
 */
enum class RelationshipStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    REVOKED,
}
