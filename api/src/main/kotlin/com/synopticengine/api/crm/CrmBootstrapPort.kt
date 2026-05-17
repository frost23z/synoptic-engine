package com.synopticengine.api.crm

/**
 * Defaults that the identity module needs to seed when provisioning a new tenant.
 * Called from inside a [com.synopticengine.api.shared.TenantContext.runAs] block,
 * so all inserts implicitly carry the new tenant id.
 */
interface CrmBootstrapPort {
    /** Insert a default pipeline + stages if none exists in the current tenant. */
    fun seedDefaultPipeline()

    /** Insert default lead sources (Website, Referral, Cold Outreach, Social Media, Event). */
    fun seedDefaultLeadSources()

    /** Insert default lead types (Inbound, Outbound, Partner). */
    fun seedDefaultLeadTypes()
}
