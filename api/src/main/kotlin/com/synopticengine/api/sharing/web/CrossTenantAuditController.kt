package com.synopticengine.api.sharing.web

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.shared.web.PageResponse
import com.synopticengine.api.sharing.domain.CrossTenantAction
import com.synopticengine.api.sharing.domain.CrossTenantAudit
import com.synopticengine.api.sharing.service.CrossTenantAuditService
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Owners use this to see who touched their records (across the tenant boundary).
 * Admins of any tenant may also browse by actor tenant.
 */
@RestController
@RequestMapping($$"${api.base-path}/cross-tenant-audit")
class CrossTenantAuditController(
    private val auditService: CrossTenantAuditService,
) {
    @GetMapping
    @PreAuthorize(
        "hasAuthority('records.share') || hasAuthority('records.reshare') || hasAuthority('relationships.view')",
    )
    fun query(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam resourceType: String?,
        @RequestParam resourceId: UUID?,
        @RequestParam actorTenantId: UUID?,
        @RequestParam ownerTenantId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<CrossTenantAuditDto>> {
        val pageable = PageRequest.of(page, size)
        val results =
            if (resourceType != null && resourceId != null) {
                // Owner view of a single record.
                auditService.byOwnerResource(principal.tenantId, resourceType, resourceId, pageable)
            } else if (ownerTenantId != null && ownerTenantId == principal.tenantId) {
                // Owner view across every record this tenant owns.
                auditService.byOwner(ownerTenantId, pageable)
            } else if (actorTenantId != null && actorTenantId == principal.tenantId) {
                // Self view of "everything I did across borders".
                auditService.byActor(actorTenantId, pageable)
            } else {
                return ResponseEntity.badRequest().build()
            }
        return ResponseEntity.ok(PageResponse.of(results, CrossTenantAuditDto::from))
    }
}

data class CrossTenantAuditDto(
    val id: UUID,
    val ownerTenantId: UUID,
    val actorTenantId: UUID,
    val actorUserId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val action: CrossTenantAction,
    val payloadJson: String?,
    val at: Instant?,
) {
    companion object {
        fun from(a: CrossTenantAudit) =
            CrossTenantAuditDto(
                id = a.id!!,
                ownerTenantId = a.ownerTenantId,
                actorTenantId = a.actorTenantId,
                actorUserId = a.actorUserId,
                resourceType = a.resourceType,
                resourceId = a.resourceId,
                action = a.action,
                payloadJson = a.payloadJson,
                at = a.at,
            )
    }
}
