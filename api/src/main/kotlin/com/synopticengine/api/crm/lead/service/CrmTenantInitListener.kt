package com.synopticengine.api.crm.lead.service

import com.synopticengine.api.crm.CrmBootstrapPort
import com.synopticengine.api.identity.TenantProvisionedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Seeds the CRM defaults (pipeline + stages, lead sources, lead types) when a tenant is
 * provisioned. Runs synchronously inside the same transaction as the provisioning, so
 * a failure here rolls back the tenant creation.
 *
 * Idempotent: each seed step checks for existing data first.
 */
@Component
class CrmTenantInitListener(
    private val crmBootstrapPort: CrmBootstrapPort,
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    fun onTenantProvisioned(event: TenantProvisionedEvent) {
        // TenantContext is set by TenantProvisioningService.seedTenantDefaults via runAs.
        crmBootstrapPort.seedDefaultPipeline()
        crmBootstrapPort.seedDefaultLeadSources()
        crmBootstrapPort.seedDefaultLeadTypes()
    }
}
