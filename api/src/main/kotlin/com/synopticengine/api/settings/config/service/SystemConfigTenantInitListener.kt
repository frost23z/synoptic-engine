package com.synopticengine.api.settings.config.service

import com.synopticengine.api.identity.TenantProvisionedEvent
import com.synopticengine.api.settings.config.domain.SystemConfig
import com.synopticengine.api.settings.config.repo.SystemConfigRepository
import com.synopticengine.api.shared.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Seeds the system_configs catalogue rows for a newly-provisioned tenant.
 *
 * V012 promoted the primary key from `code` alone to `(tenant_id, code)` so
 * each tenant now needs its own copy of the catalogue. This listener copies
 * the catalogue from the seed tenant — populated by V008 — into the new
 * tenant at provisioning time. Idempotent: re-running for the same tenant
 * skips codes that already exist (it would otherwise hit the composite PK).
 */
@Component
class SystemConfigTenantInitListener(
    private val repository: SystemConfigRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    fun onTenantProvisioned(event: TenantProvisionedEvent) {
        // TenantContext is already set to the new tenant by
        // TenantProvisioningService.seedTenantDefaults via runAs; new SystemConfig
        // rows pick up that tenantId in their @PrePersist hook.
        val newTenantId = TenantContext.get() ?: error("TenantContext missing in onTenantProvisioned")
        if (newTenantId == TenantContext.SEED_TENANT_ID) {
            // Seed tenant already has its catalogue (V008); avoid double-insertion.
            return
        }

        val catalogue = repository.findAllForTenantRaw(TenantContext.SEED_TENANT_ID)
        if (catalogue.isEmpty()) {
            log.warn("Seed tenant has no system_configs catalogue rows; nothing to copy to $newTenantId")
            return
        }

        // Existing codes (if the listener is re-fired, e.g. on retry) — skip them.
        val existing = repository.findAllByOrderByGroupNameAscSortOrderAsc().map { it.code }.toSet()
        catalogue.asSequence().filter { it.code !in existing }.forEach { src ->
            repository.save(
                SystemConfig().apply {
                    // tenantId is set by @PrePersist from TenantContext (= newTenantId).
                    this.code = src.code
                    this.value = null // tenant-specific values start blank
                    this.groupName = src.groupName
                    this.label = src.label
                    this.type = src.type
                    this.isSecret = src.isSecret
                    this.sortOrder = src.sortOrder
                },
            )
        }
        log.info("Seeded ${catalogue.size} system_configs catalogue row(s) for tenant $newTenantId")
    }
}
