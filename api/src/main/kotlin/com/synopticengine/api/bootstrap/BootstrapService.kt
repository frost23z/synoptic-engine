package com.synopticengine.api.bootstrap

import com.synopticengine.api.identity.BootstrapPort
import com.synopticengine.api.inventory.InventoryPermissions
import com.synopticengine.api.shared.bootstrap.PermissionRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BootstrapService(
    private val registries: List<PermissionRegistry>,
    private val bootstrapPort: BootstrapPort,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${synoptic.admin.email:admin@synoptic.dev}")
    private val adminEmail: String,
    @Value("\${synoptic.admin.password:#{null}}")
    private val adminPassword: String?,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun onApplicationReady() {
        log.info("Bootstrap: seeding permissions, roles, and admin user")
        upsertPermissions()
        upsertDefaultRoles()
        upsertAdminUser()
        log.info("Bootstrap: complete")
    }

    private fun upsertPermissions() {
        val allDefinitions = registries.flatMap { it.permissions() }
        val existingKeys = bootstrapPort.allPermissionNames()
        val toCreate = allDefinitions.filter { it.key !in existingKeys }
        toCreate.forEach { def -> bootstrapPort.ensurePermission(def.key, def.description) }
        if (toCreate.isNotEmpty()) {
            log.info("Bootstrap: created ${toCreate.size} permissions")
        }
    }

    private fun upsertDefaultRoles() {
        val allPermissionNames = bootstrapPort.allPermissionNames()

        fun matching(predicate: (String) -> Boolean): Set<String> = allPermissionNames.filter(predicate).toSet()

        bootstrapPort.upsertRole(
            name = "ADMIN",
            description = "Full system access",
            permissionNames = allPermissionNames,
        )
        bootstrapPort.upsertRole(
            name = "MANAGER",
            description = "Manage team leads and reports",
            permissionNames =
                matching { key ->
                    key != "users.delete" && key != "roles.edit"
                },
        )
        bootstrapPort.upsertRole(
            name = "SALESPERSON",
            description = "Manage own leads and activities",
            permissionNames =
                matching { key ->
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
                },
        )
        bootstrapPort.upsertRole(
            name = "VIEWER",
            description = "Read-only access",
            permissionNames =
                matching { key ->
                    key.endsWith(".view") && !key.startsWith("imports")
                },
        )
    }

    private fun upsertAdminUser() {
        val password = adminPassword
        if (password == null) {
            log.info("Bootstrap: SYNOPTIC_ADMIN_PASSWORD not set — skipping admin user creation")
            return
        }
        bootstrapPort.ensureAdminUser(
            email = adminEmail,
            encodedPassword = passwordEncoder.encode(password).toString(),
            adminRoleName = "ADMIN",
        )
        log.info("Bootstrap: ensured admin user $adminEmail")
    }
}
