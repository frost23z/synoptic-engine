package com.synopticengine.api

import com.synopticengine.api.identity.TenantApi
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
    @Autowired
    lateinit var tenantApi: TenantApi

    @Test
    fun `two provisioned tenants are isolated end-to-end`() {
        val slugA = "isolation-a-${UUID.randomUUID().toString().take(6)}"
        val slugB = "isolation-b-${UUID.randomUUID().toString().take(6)}"
        val adminAEmail = "admin-a-${UUID.randomUUID()}@test.com"
        val adminBEmail = "admin-b-${UUID.randomUUID()}@test.com"
        val password = "Password123!"

        val tenantA = tenantApi.provision("Tenant A", slugA, adminAEmail, password)
        val tenantB = tenantApi.provision("Tenant B", slugB, adminBEmail, password)
        assertNotEquals(tenantA.id, tenantB.id)

        val tokenA = login(adminAEmail, password)
        val tokenB = login(adminBEmail, password)

        // Each tenant has its own default pipeline.
        val (pipelineIdA, stageIdA) = defaultPipelineAndStage(tokenA)
        val (pipelineIdB, _) = defaultPipelineAndStage(tokenB)
        assertNotEquals(pipelineIdA, pipelineIdB, "Each tenant must have its own pipeline ids")

        // Admin A creates a lead. It must be invisible to admin B.
        val createResult =
            post(
                "/api/leads",
                tokenA,
                mapOf(
                    "title" to "Tenant A's lead",
                    "amount" to 1000,
                    "pipelineId" to pipelineIdA,
                    "stageId" to stageIdA,
                ),
            )
        assertEquals(
            201,
            createResult.status(),
            "Expected 201 but got ${createResult.status()}: ${createResult.response.contentAsString}",
        )
        val leadId = createResult.bodyAsMap()!!["id"] as String
        assertNotNull(leadId)

        // Tenant A sees their lead.
        val aListResult = get("/api/leads", tokenA)
        assertEquals(200, aListResult.status())
        val aList = aListResult.bodyAsMap()!!

        @Suppress("UNCHECKED_CAST")
        val aContent = aList["content"] as List<Map<String, Any>>
        assertTrue(aContent.any { it["id"] == leadId }, "Tenant A should see its own lead")

        // Tenant B does not see Tenant A's lead.
        val bListResult = get("/api/leads", tokenB)
        assertEquals(200, bListResult.status())
        val bList = bListResult.bodyAsMap()!!

        @Suppress("UNCHECKED_CAST")
        val bContent = bList["content"] as List<Map<String, Any>>
        assertTrue(bContent.none { it["id"] == leadId }, "Tenant B must not see Tenant A's lead")

        // Tenant B cannot fetch the lead by id either.
        val bGetResult = get("/api/leads/$leadId", tokenB)
        assertTrue(
            bGetResult.status() == 404 || bGetResult.status() == 403,
            "Expected 404/403 fetching another tenant's lead, got ${bGetResult.status()}: ${bGetResult.response.contentAsString}",
        )
    }

    @Test
    fun `admin role created in a new tenant authorises endpoints via ALL bypass`() {
        val slug = "wildcard-${UUID.randomUUID().toString().take(6)}"
        val adminEmail = "admin-wild-${UUID.randomUUID()}@test.com"
        val password = "Password123!"

        tenantApi.provision("Wildcard tenant", slug, adminEmail, password)
        val token = login(adminEmail, password)

        // `tenants.view` is a permission added late; only ALL roles get it implicitly.
        // The provisioned ADMIN should reach the tenants list without 403.
        val result = get("/api/tenants", token)
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

    private fun defaultPipelineAndStage(token: String): Pair<String, String> {
        val pipelines =
            get("/api/pipelines", token).bodyAsList()
                ?: error("Expected pipelines list to be available")
        val defaultPipeline =
            pipelines.firstOrNull { it["isDefault"] == true }
                ?: error("No default pipeline found via API")
        val pipelineId = defaultPipeline["id"] as String

        @Suppress("UNCHECKED_CAST")
        val stages = defaultPipeline["stages"] as List<Map<String, Any>>
        val firstStageId = stages.first()["id"] as String
        return pipelineId to firstStageId
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
