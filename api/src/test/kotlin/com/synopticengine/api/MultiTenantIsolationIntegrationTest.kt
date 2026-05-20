package com.synopticengine.api

import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.PipelineResolver
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 0 verification: two tenants are provisioned independently, each admin can only
 * see and write data inside their own tenant. Without this isolation, cross-company
 * sharing is meaningless (you'd be giving access you can't take back).
 */
class MultiTenantIsolationIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantApi: TenantApi

    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var pipelineResolver: PipelineResolver

    @Test
    fun `two provisioned tenants are isolated end-to-end`() {
        val a = tenantProvisioner.provision("isolation-a")
        val b = tenantProvisioner.provision("isolation-b")
        assertNotEquals(a.tenantId, b.tenantId)

        // Each tenant has its own default pipeline.
        val pipelineA = pipelineResolver.defaultPipelineAndStage(a.token)
        val pipelineB = pipelineResolver.defaultPipelineAndStage(b.token)
        assertNotEquals(pipelineA.pipelineId, pipelineB.pipelineId, "Each tenant must have its own pipeline ids")

        // Admin A creates a lead. It must be invisible to admin B.
        val leadMap = leadFactory.create(a.token, title = "Tenant A's lead", amount = 1000)
        val leadId = leadMap["id"] as String
        assertNotNull(leadId)

        @Suppress("UNCHECKED_CAST")
        val aContent = get("/api/leads", a.token).bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(aContent.any { it["id"] == leadId }, "Tenant A should see its own lead")

        @Suppress("UNCHECKED_CAST")
        val bContent = get("/api/leads", b.token).bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(bContent.none { it["id"] == leadId }, "Tenant B must not see Tenant A's lead")

        val bGet = get("/api/leads/$leadId", b.token)
        assertTrue(
            bGet.status() == 404 || bGet.status() == 403,
            "Expected 404/403 fetching another tenant's lead, got ${bGet.status()}",
        )
    }

    @Test
    fun `admin role created in a new tenant authorises endpoints via ALL bypass`() {
        val tenant = tenantProvisioner.provision("wildcard")
        // `tenants.view` is a permission added late; only ALL roles get it implicitly.
        // The provisioned ADMIN should reach the tenants list without 403.
        val result = get("/api/tenants", tenant.token)
        assertEquals(
            200,
            result.status(),
            "ADMIN should bypass permission checks; got ${result.status()}: ${result.response.contentAsString}",
        )
    }

    @Test
    fun `provisioning the same slug twice fails fast`() {
        val slug = "dup-${UUID.randomUUID().toString().take(6)}"
        tenantApi.provision("Once", slug, "one-${UUID.randomUUID()}@test.com", "Password123!")
        val ex =
            assertThrows<IllegalStateException> {
                tenantApi.provision("Twice", slug, "two-${UUID.randomUUID()}@test.com", "Password123!")
            }
        assertTrue(ex.message!!.contains("already exists"))
    }

    private inline fun <reified T : Throwable> assertThrows(noinline block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("Expected ${T::class.simpleName}, got ${e::class.simpleName}: ${e.message}")
        }
        throw AssertionError("Expected ${T::class.simpleName} to be thrown, but no exception was thrown")
    }
}
