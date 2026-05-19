package com.synopticengine.api.sharing.service

import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.ResourceType

/**
 * Default cascade rules — see analysis/03-cross-company-sharing.md § 7.
 *
 * When a record is shared, certain related records become implicitly shared too.
 * Defaults below; per-policy cascade is overridden via `tenant_share_policies.cascade_jsonb`
 * and per-record-share cascade is currently the defaults only (no per-share override yet).
 *
 * The cascade is expressed as: for parent (type, id), which (type, id) sub-resources are
 * also shared and at which [AccessLevel]. Sub-resource discovery requires inspecting the
 * parent record (e.g. lead.personId, lead.organizationId); the caller provides those.
 */
data class CascadeTarget(
    val resourceType: ResourceType,
    val resourceId: java.util.UUID,
    val accessLevel: AccessLevel,
)

/**
 * Lookup for default cascade rules.
 *
 * | Shared parent      | Default cascade                                                |
 * |--------------------|----------------------------------------------------------------|
 * | leads              | persons (parent.personId) READ, organizations (parent.orgId) READ |
 * | quotes             | leads (parent.leadId) READ, persons (parent.personId) READ      |
 * | other types        | no default cascade                                              |
 */
object CascadeRules {
    fun defaultCascadeFor(
        parentType: ResourceType,
        personId: java.util.UUID?,
        organizationId: java.util.UUID?,
        leadId: java.util.UUID? = null,
    ): List<CascadeTarget> =
        when (parentType) {
            ResourceType.LEADS -> {
                buildList {
                    if (personId != null) add(CascadeTarget(ResourceType.PERSONS, personId, AccessLevel.READ))
                    if (organizationId !=
                        null
                    ) {
                        add(CascadeTarget(ResourceType.ORGANIZATIONS, organizationId, AccessLevel.READ))
                    }
                }
            }

            ResourceType.QUOTES -> {
                buildList {
                    if (leadId != null) add(CascadeTarget(ResourceType.LEADS, leadId, AccessLevel.READ))
                    if (personId != null) add(CascadeTarget(ResourceType.PERSONS, personId, AccessLevel.READ))
                }
            }

            else -> {
                emptyList()
            }
        }
}
