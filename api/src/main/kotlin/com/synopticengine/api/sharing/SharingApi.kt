package com.synopticengine.api.sharing

import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.CrossTenantAction
import java.util.UUID

/**
 * Exposed cross-module port for the sharing module.
 *
 * Other modules (CRM, Inventory) call this from their service layer when:
 *  - A record is being mutated and they need to know whether the current tenant is the
 *    owner or a consumer ([effectiveAccess]).
 *  - A cross-tenant action has just succeeded and needs auditing ([recordAudit]).
 *
 * The methods are intentionally narrow so callers don't reach into [sharing] internals.
 */
interface SharingApi {
    /**
     * What level of access does [consumerTenantId] currently have on a record of
     * [resourceType] with id [resourceId]? Returns NONE if no visibility row applies.
     *
     * Cheap — single index lookup on resource_visibility.
     */
    fun effectiveAccess(
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ): AccessLevel

    /** Append a row to cross_tenant_audit. Caller has already validated ownerTenantId != actorTenantId. */
    fun recordAudit(
        ownerTenantId: UUID,
        actorTenantId: UUID,
        actorUserId: UUID,
        resourceType: String,
        resourceId: UUID,
        action: CrossTenantAction,
        payloadJson: String? = null,
    )
}
