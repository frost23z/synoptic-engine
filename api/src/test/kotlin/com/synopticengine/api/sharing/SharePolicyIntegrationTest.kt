package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.identity.TenantApi
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 2 / Sprint 2a: share policy CRUD. No enforcement yet — these only verify the
 * shape of the policy table and that admin permissions are wired correctly.
 */
class SharePolicyIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var tenantApi: TenantApi

    @Test
    fun `create and list and revoke a leads policy`() {
        val (tenantA, tokenA) = provision("policyA")
        val (tenantB, tokenB) = provision("policyB")

        val relId =
            (
                post(
                    "/api/relationships",
                    tokenA,
                    mapOf("targetTenantId" to tenantB.toString(), "type" to "PARENT_CHILD"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$relId/accept", tokenB)

        val createResp =
            post(
                "/api/relationships/$relId/policies",
                tokenA,
                mapOf(
                    "resourceType" to "leads",
                    "accessLevel" to "READ",
                    "materialize" to true,
                ),
            )
        assertEquals(201, createResp.status(), createResp.response.contentAsString)
        val policyId = createResp.bodyAsMap()!!["id"] as String
        assertEquals("READ", createResp.bodyAsMap()!!["accessLevel"])
        assertEquals("leads", createResp.bodyAsMap()!!["resourceType"])

        // Target tenant can see policies governing them.
        val viewByTarget = get("/api/relationships/$relId/policies", tokenB)
        assertEquals(200, viewByTarget.status())
        val targetList = viewByTarget.bodyAsList()!!
        assertTrue(targetList.any { it["id"] == policyId })

        // Target tenant CANNOT manage policies (source tenant owns them).
        val targetTryUpdate =
            put(
                "/api/share-policies/$policyId",
                tokenB,
                mapOf("accessLevel" to "WRITE"),
            )
        assertEquals(403, targetTryUpdate.status())

        // Source updates access level.
        val update =
            put(
                "/api/share-policies/$policyId",
                tokenA,
                mapOf("accessLevel" to "WRITE"),
            )
        assertEquals(200, update.status(), update.response.contentAsString)
        assertEquals("WRITE", update.bodyAsMap()!!["accessLevel"])

        // Revoke.
        val revoke = delete("/api/share-policies/$policyId", tokenA)
        assertEquals(200, revoke.status())

        // After revoke, soft-restriction filters the policy out of the relationship's list.
        val afterRevoke = get("/api/relationships/$relId/policies", tokenA).bodyAsList()!!
        assertTrue(afterRevoke.none { it["id"] == policyId }, "Revoked policy should not appear in active list")
    }

    @Test
    fun `duplicate policy for same resource fails`() {
        val (tenantA, tokenA) = provision("dupPolA")
        val (tenantB, tokenB) = provision("dupPolB")
        val relId =
            (
                post(
                    "/api/relationships",
                    tokenA,
                    mapOf("targetTenantId" to tenantB.toString(), "type" to "PARTNER"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$relId/accept", tokenB)

        post(
            "/api/relationships/$relId/policies",
            tokenA,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        ).also { assertEquals(201, it.status()) }

        val dup =
            post(
                "/api/relationships/$relId/policies",
                tokenA,
                mapOf("resourceType" to "leads", "accessLevel" to "WRITE"),
            )
        assertEquals(409, dup.status())
    }

    @Test
    fun `unknown resource type is rejected`() {
        val (tenantA, tokenA) = provision("unkA")
        val (tenantB, tokenB) = provision("unkB")
        val relId =
            (
                post(
                    "/api/relationships",
                    tokenA,
                    mapOf("targetTenantId" to tenantB.toString(), "type" to "PARTNER"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$relId/accept", tokenB)

        val bad =
            post(
                "/api/relationships/$relId/policies",
                tokenA,
                mapOf("resourceType" to "not-a-thing", "accessLevel" to "READ"),
            )
        assertEquals(400, bad.status())
    }

    private fun provision(prefix: String): Pair<UUID, String> {
        val slug = "$prefix-${UUID.randomUUID().toString().take(6)}"
        val adminEmail = "$prefix-${UUID.randomUUID()}@test.com"
        val password = "Password123!"
        val summary = tenantApi.provision(prefix.replaceFirstChar { it.titlecase() }, slug, adminEmail, password)
        return summary.id to login(adminEmail, password)
    }
}
