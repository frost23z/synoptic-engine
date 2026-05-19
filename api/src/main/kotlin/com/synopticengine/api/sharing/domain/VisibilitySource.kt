package com.synopticengine.api.sharing.domain

/**
 * Why a [ResourceVisibility] row exists:
 *
 * - `POLICY` — materialized by a [TenantSharePolicy] applying to this resource.
 * - `RECORD` — created by an explicit per-record share (Sprint 2c).
 * - `CASCADE` — implicit grant because a related resource was shared (e.g. lead → person).
 */
enum class VisibilitySource {
    POLICY,
    RECORD,
    CASCADE,
    ;

    val literal: String get() = name.lowercase()

    companion object {
        fun fromLiteral(literal: String): VisibilitySource =
            entries.firstOrNull { it.literal == literal.lowercase() }
                ?: throw IllegalArgumentException("Unknown visibility source: $literal")
    }
}
