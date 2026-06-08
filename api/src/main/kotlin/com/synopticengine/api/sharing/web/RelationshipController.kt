package com.synopticengine.api.sharing.web

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.sharing.SharingPermissions
import com.synopticengine.api.sharing.service.TenantRelationshipService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/relationships")
class RelationshipController(
    private val service: TenantRelationshipService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('${SharingPermissions.RELATIONSHIPS_VIEW}')")
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<List<RelationshipResponse>> {
        val list = service.listFor(principal.tenantId).map(RelationshipResponse::from)
        return ResponseEntity.ok(list)
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('${SharingPermissions.RELATIONSHIPS_VIEW}')")
    fun get(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<RelationshipResponse> =
        ResponseEntity.ok(RelationshipResponse.from(service.get(id, principal.tenantId)))

    @PostMapping
    @PreAuthorize("hasAuthority('${SharingPermissions.RELATIONSHIPS_MANAGE}')")
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: CreateRelationshipRequest,
    ): ResponseEntity<RelationshipResponse> {
        val rel =
            service.request(
                sourceTenantId = principal.tenantId,
                targetTenantId = request.targetTenantId!!,
                relationshipType = request.type!!,
                initiatedBy = principal.id,
                note = request.note,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(RelationshipResponse.from(rel))
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('${SharingPermissions.RELATIONSHIPS_MANAGE}')")
    fun accept(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<RelationshipResponse> =
        ResponseEntity.ok(
            RelationshipResponse.from(service.accept(id, principal.tenantId, principal.id)),
        )

    @PatchMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('${SharingPermissions.RELATIONSHIPS_MANAGE}')")
    fun revoke(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<RelationshipResponse> =
        ResponseEntity.ok(RelationshipResponse.from(service.revoke(id, principal.tenantId)))

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('${SharingPermissions.RELATIONSHIPS_MANAGE}')")
    fun suspend(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<RelationshipResponse> =
        ResponseEntity.ok(RelationshipResponse.from(service.suspend(id, principal.tenantId)))

    @PatchMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('${SharingPermissions.RELATIONSHIPS_MANAGE}')")
    fun resume(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<RelationshipResponse> =
        ResponseEntity.ok(RelationshipResponse.from(service.resume(id, principal.tenantId)))
}
