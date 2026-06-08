package com.synopticengine.api.sharing.web

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.sharing.SharingPermissions
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.RecordShare
import com.synopticengine.api.sharing.service.RecordShareService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("")
class RecordShareController(
    private val service: RecordShareService,
) {
    /** Owner-side share: only the owner tenant may call this endpoint. */
    @PostMapping("/records/share")
    @PreAuthorize("hasAuthority('${SharingPermissions.RECORDS_SHARE}')")
    fun share(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: ShareRecordRequest,
    ): ResponseEntity<RecordShareResponse> {
        val share =
            service.share(
                ownerTenantId = principal.tenantId,
                consumerTenantId = request.consumerTenantId!!,
                resourceType = request.resourceType!!,
                resourceId = request.resourceId!!,
                accessLevel = request.accessLevel!!,
                sharedBy = principal.id,
                expiresAt = request.expiresAt,
                note = request.note,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(RecordShareResponse.from(share))
    }

    /**
     * Consumer-initiated reshare: a consumer with MANAGE access may share the
     * record onward to another tenant. Gated on [SharingPermissions.RECORDS_RESHARE];
     * the service additionally checks [AccessLevel.canReshare] on the effective
     * access level of the calling tenant.
     */
    @PostMapping("/records/reshare")
    @PreAuthorize("hasAuthority('${SharingPermissions.RECORDS_RESHARE}')")
    fun reshare(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: ReshareRecordRequest,
    ): ResponseEntity<RecordShareResponse> {
        val share =
            service.reshare(
                actingTenantId = principal.tenantId,
                actingUserId = principal.id,
                consumerTenantId = request.consumerTenantId!!,
                resourceType = request.resourceType!!,
                resourceId = request.resourceId!!,
                accessLevel = request.accessLevel!!,
                expiresAt = request.expiresAt,
                note = request.note,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(RecordShareResponse.from(share))
    }

    @DeleteMapping("/records/share/{id}")
    @PreAuthorize("hasAuthority('${SharingPermissions.RECORDS_SHARE}')")
    fun revoke(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<RecordShareResponse> =
        ResponseEntity.ok(RecordShareResponse.from(service.revoke(id, principal.tenantId, principal.id)))

    @GetMapping("/records/{resourceType}/{resourceId}/shares")
    @PreAuthorize("hasAuthority('${SharingPermissions.RECORDS_SHARE}')")
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable resourceType: String,
        @PathVariable resourceId: UUID,
    ): ResponseEntity<List<RecordShareResponse>> =
        ResponseEntity.ok(
            service
                .listShares(principal.tenantId, resourceType, resourceId)
                .map(RecordShareResponse::from),
        )

    @GetMapping("/records/shared-with-me")
    @PreAuthorize("hasAuthority('${SharingPermissions.RECORDS_SHARE}')")
    fun listSharedWithMe(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) resourceType: String?,
    ): ResponseEntity<List<RecordShareResponse>> =
        ResponseEntity.ok(
            service
                .listSharedWithMe(principal.tenantId, resourceType)
                .map(RecordShareResponse::from),
        )
}

data class ShareRecordRequest(
    @field:NotNull
    val consumerTenantId: UUID?,
    @field:NotBlank
    val resourceType: String?,
    @field:NotNull
    val resourceId: UUID?,
    @field:NotNull
    val accessLevel: AccessLevel?,
    val expiresAt: Instant? = null,
    val note: String? = null,
)

/**
 * Request DTO for the consumer-initiated reshare endpoint.
 * Same fields as [ShareRecordRequest]; separate type for clarity.
 */
data class ReshareRecordRequest(
    @field:NotNull
    val consumerTenantId: UUID?,
    @field:NotBlank
    val resourceType: String?,
    @field:NotNull
    val resourceId: UUID?,
    @field:NotNull
    val accessLevel: AccessLevel?,
    val expiresAt: Instant? = null,
    val note: String? = null,
)

data class RecordShareResponse(
    val id: UUID,
    val ownerTenantId: UUID,
    val consumerTenantId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val accessLevel: AccessLevel,
    val sharedBy: UUID,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
    val note: String?,
    val createdAt: Instant?,
) {
    companion object {
        fun from(s: RecordShare) =
            RecordShareResponse(
                id = s.id!!,
                ownerTenantId = s.ownerTenantId,
                consumerTenantId = s.consumerTenantId,
                resourceType = s.resourceType,
                resourceId = s.resourceId,
                accessLevel = s.accessLevel,
                sharedBy = s.sharedBy,
                expiresAt = s.expiresAt,
                revokedAt = s.revokedAt,
                note = s.note,
                createdAt = s.createdAt,
            )
    }
}
