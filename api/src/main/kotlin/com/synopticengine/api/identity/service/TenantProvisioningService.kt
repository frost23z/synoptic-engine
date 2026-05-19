package com.synopticengine.api.identity.service

import com.synopticengine.api.identity.BootstrapPort
import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.identity.TenantProvisionedEvent
import com.synopticengine.api.identity.TenantSummary
import com.synopticengine.api.identity.domain.Tenant
import com.synopticengine.api.identity.repo.PermissionRepository
import com.synopticengine.api.identity.repo.TenantRepository
import com.synopticengine.api.inventory.InventoryPermissions
import com.synopticengine.api.shared.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Default [TenantApi] implementation.
 *
 * Identity-owned seeding (roles, admin user) happens inline. CRM defaults are decoupled
 * via [TenantProvisionedEvent] so the identity module doesn't need to import anything
 * from CRM. Listeners run synchronously inside the same transaction.
 */
@Service
internal class TenantProvisioningService(
    private val tenantRepository: TenantRepository,
    private val bootstrapPort: BootstrapPort,
    private val permissionRepository: PermissionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val eventPublisher: ApplicationEventPublisher,
) : TenantApi {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun provision(
        name: String,
        slug: String,
        adminEmail: String,
        adminPassword: String,
    ): TenantSummary {
        require(slug.isNotBlank()) { "Tenant slug must not be blank" }
        require(name.isNotBlank()) { "Tenant name must not be blank" }
        require(adminPassword.length >= 8) { "Admin password must be at least 8 characters" }
        if (tenantRepository.existsBySlug(slug)) {
            throw IllegalStateException("Tenant with slug '$slug' already exists")
        }

        val tenant =
            tenantRepository.save(
                Tenant().apply {
                    this.name = name
                    this.slug = slug
                },
            )

        log.info("Provisioning tenant ${tenant.id} ($slug); seeding defaults")
        seedTenantDefaults(tenant.id!!, adminEmail, adminPassword)
        log.info("Tenant ${tenant.id} provisioned")
        return tenant.toSummary()
    }

    @Transactional
    override fun seedTenantDefaults(
        tenantId: UUID,
        adminEmail: String?,
        adminPassword: String?,
    ) {
        TenantContext.runAs(tenantId) {
            seedDefaultRoles()
            if (adminEmail != null && adminPassword != null) {
                bootstrapPort.ensureAdminUser(
                    email = adminEmail,
                    encodedPassword = passwordEncoder.encode(adminPassword)!!,
                    adminRoleName = "ADMIN",
                )
            }
            eventPublisher.publishEvent(TenantProvisionedEvent(tenantId, tenantSlugOrUnknown(tenantId)))
        }
    }

    private fun tenantSlugOrUnknown(tenantId: UUID): String =
        tenantRepository
            .findById(tenantId)
            .map {
                it.slug
            }.orElse("unknown")

    override fun exists(tenantId: UUID): Boolean = tenantRepository.existsById(tenantId)

    override fun findSummary(tenantId: UUID): TenantSummary? =
        tenantRepository.findById(tenantId).map { it.toSummary() }.orElse(null)

    private fun seedDefaultRoles() {
        val allPermissionNames = permissionRepository.findAll().map { it.key }.toSet()

        bootstrapPort.upsertRole(
            name = "ADMIN",
            description = "Full system access",
            permissionNames = emptySet(),
            wildcardPermissions = true,
        )
        bootstrapPort.upsertRole(
            name = "MANAGER",
            description = "Manage team leads and reports",
            permissionNames =
                allPermissionNames
                    .filter { key ->
                        key != "users.delete" && key != "roles.edit"
                    }.toSet(),
        )
        bootstrapPort.upsertRole(
            name = "SALESPERSON",
            description = "Manage own leads and activities",
            permissionNames =
                allPermissionNames
                    .filter { key ->
                        (
                            key.startsWith("leads") ||
                                key.startsWith("contacts") ||
                                key.startsWith("activities") ||
                                key.startsWith("quotes") ||
                                key.startsWith("tags") ||
                                key.startsWith("mail") ||
                                key.startsWith("pipelines") ||
                                key == "reports.view" ||
                                key == InventoryPermissions.PRODUCTS_VIEW
                        ) &&
                            key.contains(".") &&
                            !key.endsWith(".delete")
                    }.toSet(),
        )
        bootstrapPort.upsertRole(
            name = "VIEWER",
            description = "Read-only access",
            permissionNames = allPermissionNames.filter { it.endsWith(".view") && !it.startsWith("imports") }.toSet(),
        )
    }

    private fun Tenant.toSummary() =
        TenantSummary(
            id = id!!,
            name = name,
            slug = slug,
            status = status.name,
        )
}
