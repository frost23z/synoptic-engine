package com.synopticengine.api.bootstrap

import com.synopticengine.api.identity.BootstrapPort
import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.bootstrap.PermissionRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * One-time application bootstrap. Runs on every startup but is idempotent.
 *
 * - Inserts any new permission definitions discovered from [PermissionRegistry] beans (global catalog).
 * - Seeds the **seed tenant** defaults (roles, default pipeline, lead sources/types) and the admin user.
 *   Other tenants are provisioned through [TenantApi.provision].
 */
@Service
class BootstrapService(
    private val registries: List<PermissionRegistry>,
    private val bootstrapPort: BootstrapPort,
    private val tenantApi: TenantApi,
    @Value("\${synoptic.admin.email:admin@synoptic.dev}")
    private val adminEmail: String,
    @Value("\${synoptic.admin.password:#{null}}")
    private val adminPassword: String?,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun onApplicationReady() {
        log.info("Bootstrap: seeding permissions and seed tenant defaults")
        upsertPermissions()
        tenantApi.seedTenantDefaults(
            tenantId = TenantContext.SEED_TENANT_ID,
            adminEmail = adminEmail.takeIf { adminPassword != null },
            adminPassword = adminPassword,
        )
        if (adminPassword == null) {
            log.info("Bootstrap: SYNOPTIC_ADMIN_PASSWORD not set — skipped admin user creation for seed tenant")
        } else {
            log.info("Bootstrap: ensured admin user $adminEmail in seed tenant")
        }
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
}
