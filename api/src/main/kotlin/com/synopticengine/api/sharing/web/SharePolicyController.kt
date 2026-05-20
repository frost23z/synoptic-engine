package com.synopticengine.api.sharing.web

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.sharing.SharingPermissions
import com.synopticengine.api.sharing.service.TenantSharePolicyService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}")
class SharePolicyController(
    private val service: TenantSharePolicyService,
) {
    @GetMapping("/relationships/{relationshipId}/policies")
    @PreAuthorize("hasAuthority('${SharingPermissions.SHARE_POLICIES_VIEW}')")
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable relationshipId: UUID,
    ): ResponseEntity<List<SharePolicyResponse>> =
        ResponseEntity.ok(
            service.listForRelationship(relationshipId, principal.tenantId).map(SharePolicyResponse::from),
        )

    @PostMapping("/relationships/{relationshipId}/policies")
    @PreAuthorize("hasAuthority('${SharingPermissions.SHARE_POLICIES_MANAGE}')")
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable relationshipId: UUID,
        @Valid @RequestBody request: CreateSharePolicyRequest,
    ): ResponseEntity<SharePolicyResponse> {
        val policy =
            service.create(
                relationshipId = relationshipId,
                actingTenantId = principal.tenantId,
                actingUserId = principal.id,
                resourceType = request.resourceType!!,
                accessLevel = request.accessLevel!!,
                filterJson = request.filterJson,
                cascadeJson = request.cascadeJson,
                materialize = request.materialize,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(SharePolicyResponse.from(policy))
    }

    @GetMapping("/share-policies/{id}")
    @PreAuthorize("hasAuthority('${SharingPermissions.SHARE_POLICIES_VIEW}')")
    fun get(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<SharePolicyResponse> =
        ResponseEntity.ok(SharePolicyResponse.from(service.get(id, principal.tenantId)))

    @PutMapping("/share-policies/{id}")
    @PreAuthorize("hasAuthority('${SharingPermissions.SHARE_POLICIES_MANAGE}')")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateSharePolicyRequest,
    ): ResponseEntity<SharePolicyResponse> =
        ResponseEntity.ok(
            SharePolicyResponse.from(
                service.update(
                    policyId = id,
                    actingTenantId = principal.tenantId,
                    accessLevel = request.accessLevel,
                    filterJson = request.filterJson,
                    cascadeJson = request.cascadeJson,
                ),
            ),
        )

    @DeleteMapping("/share-policies/{id}")
    @PreAuthorize("hasAuthority('${SharingPermissions.SHARE_POLICIES_MANAGE}')")
    fun revoke(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<SharePolicyResponse> =
        ResponseEntity.ok(SharePolicyResponse.from(service.revoke(id, principal.tenantId)))
}
