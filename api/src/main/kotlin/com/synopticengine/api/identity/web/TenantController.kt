package com.synopticengine.api.identity.web

import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.identity.TenantSummary
import com.synopticengine.api.identity.repo.TenantRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/tenants")
class TenantController(
    private val tenantRepository: TenantRepository,
    private val tenantApi: TenantApi,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('tenants.view')")
    fun list(): ResponseEntity<List<TenantResponse>> =
        ResponseEntity.ok(
            tenantRepository.findAll().map { it.toResponse() },
        )

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('tenants.view')")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<TenantResponse> {
        val tenant = tenantRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(tenant.toResponse())
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenants.manage')")
    fun provision(
        @Valid @RequestBody request: ProvisionTenantRequest,
    ): ResponseEntity<TenantResponse> {
        val tenant: TenantSummary =
            tenantApi.provision(
                name = request.name,
                slug = request.slug,
                adminEmail = request.adminEmail,
                adminPassword = request.adminPassword,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            TenantResponse(
                id = tenant.id,
                name = tenant.name,
                slug = tenant.slug,
                status = tenant.status,
                legalName = null,
                timezone = null,
                locale = null,
                createdAt = null,
                updatedAt = null,
            ),
        )
    }
}

data class ProvisionTenantRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    @field:jakarta.validation.constraints.Pattern(
        regexp = "^[a-z0-9-]+$",
        message = "Slug must contain only lowercase letters, digits, and hyphens",
    )
    val slug: String,
    @field:Email
    val adminEmail: String,
    @field:Size(min = 8, message = "Admin password must be at least 8 characters")
    val adminPassword: String,
)

data class TenantResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    val status: String,
    val legalName: String?,
    val timezone: String?,
    val locale: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

private fun com.synopticengine.api.identity.domain.Tenant.toResponse() =
    TenantResponse(
        id = id!!,
        name = name,
        slug = slug,
        status = status.name,
        legalName = legalName,
        timezone = timezone,
        locale = locale,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
