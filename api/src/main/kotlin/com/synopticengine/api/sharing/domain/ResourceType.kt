package com.synopticengine.api.sharing.domain

/**
 * Fixed enum of shareable resource families. Adding a new value requires:
 *  1. A new column in the entity, RLS policy, and Hibernate filter referencing the literal.
 *  2. An entry in the cascade defaults (see § 7 of `analysis/03-cross-company-sharing.md`).
 *
 * The string form (`literal`) is what travels over the wire and lands in
 * `resource_visibility.resource_type` / `tenant_share_policies.resource_type` /
 * `record_shares.resource_type`. The enum lives in code only.
 */
enum class ResourceType(
    val literal: String,
) {
    LEADS("leads"),
    PERSONS("contacts.persons"),
    ORGANIZATIONS("contacts.organizations"),
    PRODUCTS("products"),
    PRICELISTS("products.pricelists"),
    ACTIVITIES("leads.activities"),
    QUOTES("quotes"),
    WAREHOUSES("warehouses"),
    ;

    companion object {
        private val byLiteral = entries.associateBy { it.literal }

        fun fromLiteral(literal: String): ResourceType =
            byLiteral[literal]
                ?: throw IllegalArgumentException("Unknown resource type: '$literal'")

        fun isKnown(literal: String): Boolean = byLiteral.containsKey(literal)
    }
}
