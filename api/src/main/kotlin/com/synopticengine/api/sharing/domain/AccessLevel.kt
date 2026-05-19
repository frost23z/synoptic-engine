package com.synopticengine.api.sharing.domain

/**
 * Per-direction access granted by a policy or record share.
 *
 * | Level   | Read | Comment | Mutate | Delete | Reshare |
 * |---------|:----:|:-------:|:------:|:------:|:-------:|
 * | NONE    |      |         |        |        |         |
 * | READ    |  ✓   |         |        |        |         |
 * | COMMENT |  ✓   |    ✓    |        |        |         |
 * | WRITE   |  ✓   |    ✓    |   ✓    |        |         |
 * | MANAGE  |  ✓   |    ✓    |   ✓    |   ✓    |    ✓    |
 *
 * Effective access for a consumer on a record is the **max** across all visibility rows
 * (own + policy + cascade + record_share). See § 3.3 in `analysis/03-cross-company-sharing.md`.
 */
enum class AccessLevel {
    NONE,
    READ,
    COMMENT,
    WRITE,
    MANAGE,
    ;

    fun canRead(): Boolean = ordinal >= READ.ordinal

    fun canComment(): Boolean = ordinal >= COMMENT.ordinal

    fun canWrite(): Boolean = ordinal >= WRITE.ordinal

    fun canDelete(): Boolean = ordinal >= MANAGE.ordinal

    fun canReshare(): Boolean = ordinal >= MANAGE.ordinal

    fun isAtLeast(other: AccessLevel): Boolean = ordinal >= other.ordinal

    companion object {
        /** Returns the broader of the two levels — used to fold visibility rows together. */
        fun max(
            a: AccessLevel,
            b: AccessLevel,
        ): AccessLevel = if (a.ordinal >= b.ordinal) a else b
    }
}
