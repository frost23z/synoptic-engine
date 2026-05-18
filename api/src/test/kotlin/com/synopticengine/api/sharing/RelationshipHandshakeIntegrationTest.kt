package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.identity.TenantApi
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 2 / Sprint 2a verification: relationship handshake (request → accept → revoke).
 *
 * No enforcement yet — these tests only cover the lifecycle of the [TenantRelationship]
 * row itself. Sprint 2b will add visibility/RLS tests that show records actually flow
 * across the boundary once a relationship is active.
 */
class RelationshipHandshakeIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var tenantApi: TenantApi

    @Test
    fun `parent-child relationship round-trip`() {
        val (tenantA, tokenA) = provision("parent")
        val (tenantB, tokenB) = provision("child")

        val createResp =
            post(
                "/api/relationships",
                tokenA,
                mapOf(
                    "targetTenantId" to tenantB.toString(),
                    "type" to "PARENT_CHILD",
                    "note" to "Holdings → regional subsidiary",
                ),
            )
        assertEquals(201, createResp.status(), "request failed: ${createResp.response.contentAsString}")
        val relId = createResp.bodyAsMap()!!["id"] as String
        assertEquals("PENDING", createResp.bodyAsMap()!!["status"])
        assertEquals(tenantA.toString(), createResp.bodyAsMap()!!["sourceTenantId"])
        assertEquals(tenantB.toString(), createResp.bodyAsMap()!!["targetTenantId"])

        // Source cannot accept their own request.
        val selfAccept = patch("/api/relationships/$relId/accept", tokenA)
        assertEquals(403, selfAccept.status())

        // Target accepts.
        val acceptResp = patch("/api/relationships/$relId/accept", tokenB)
        assertEquals(200, acceptResp.status(), acceptResp.response.contentAsString)
        assertEquals("ACTIVE", acceptResp.bodyAsMap()!!["status"])
        assertNotNull(acceptResp.bodyAsMap()!!["acceptedAt"])

        // Both ends can see it in their list.
        val listA = get("/api/relationships", tokenA).bodyAsList()!!
        val listB = get("/api/relationships", tokenB).bodyAsList()!!
        assertTrue(listA.any { it["id"] == relId })
        assertTrue(listB.any { it["id"] == relId })

        // Child cannot revoke a PARENT_CHILD relationship.
        val childRevoke = patch("/api/relationships/$relId/revoke", tokenB)
        assertEquals(403, childRevoke.status())

        // Parent revokes.
        val parentRevoke = patch("/api/relationships/$relId/revoke", tokenA)
        assertEquals(200, parentRevoke.status(), parentRevoke.response.contentAsString)
        assertEquals("REVOKED", parentRevoke.bodyAsMap()!!["status"])
    }

    @Test
    fun `partner relationship can be revoked by either side`() {
        val (tenantA, tokenA) = provision("partnerA")
        val (tenantB, tokenB) = provision("partnerB")

        val createResp =
            post(
                "/api/relationships",
                tokenA,
                mapOf("targetTenantId" to tenantB.toString(), "type" to "PARTNER"),
            )
        assertEquals(201, createResp.status())
        val relId = createResp.bodyAsMap()!!["id"] as String

        patch("/api/relationships/$relId/accept", tokenB).also {
            assertEquals(200, it.status())
        }

        // Target side may revoke a PARTNER relationship.
        val revoke = patch("/api/relationships/$relId/revoke", tokenB)
        assertEquals(200, revoke.status())
        assertEquals("REVOKED", revoke.bodyAsMap()!!["status"])
    }

    @Test
    fun `duplicate request fails with conflict`() {
        val (_, tokenA) = provision("dupA")
        val (tenantB, _) = provision("dupB")

        val first =
            post(
                "/api/relationships",
                tokenA,
                mapOf("targetTenantId" to tenantB.toString(), "type" to "PARTNER"),
            )
        assertEquals(201, first.status())

        val second =
            post(
                "/api/relationships",
                tokenA,
                mapOf("targetTenantId" to tenantB.toString(), "type" to "PARTNER"),
            )
        assertEquals(409, second.status())
    }

    @Test
    fun `unrelated tenant cannot see a relationship`() {
        val (_, tokenA) = provision("hiddenA")
        val (tenantB, _) = provision("hiddenB")
        val (_, tokenC) = provision("hiddenC")

        val createResp =
            post(
                "/api/relationships",
                tokenA,
                mapOf("targetTenantId" to tenantB.toString(), "type" to "PARTNER"),
            )
        val relId = createResp.bodyAsMap()!!["id"] as String

        // Tenant C is not part of the relationship; should get 404 (not 403, to avoid leaking existence).
        val cView = get("/api/relationships/$relId", tokenC)
        assertEquals(404, cView.status())

        val cList = get("/api/relationships", tokenC).bodyAsList()!!
        assertTrue(cList.none { it["id"] == relId })
    }

    @Test
    fun `suspend and resume`() {
        val (tenantA, tokenA) = provision("suspendA")
        val (tenantB, tokenB) = provision("suspendB")

        val relId =
            (
                post(
                    "/api/relationships",
                    tokenA,
                    mapOf("targetTenantId" to tenantB.toString(), "type" to "PARTNER"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$relId/accept", tokenB)

        val sus = patch("/api/relationships/$relId/suspend", tokenA)
        assertEquals(200, sus.status())
        assertEquals("SUSPENDED", sus.bodyAsMap()!!["status"])

        val res = patch("/api/relationships/$relId/resume", tokenA)
        assertEquals(200, res.status())
        assertEquals("ACTIVE", res.bodyAsMap()!!["status"])
    }

    private fun provision(prefix: String): Pair<UUID, String> {
        val slug = "$prefix-${UUID.randomUUID().toString().take(6)}"
        val adminEmail = "$prefix-${UUID.randomUUID()}@test.com"
        val password = "Password123!"
        val summary = tenantApi.provision(prefix.replaceFirstChar { it.titlecase() }, slug, adminEmail, password)
        return summary.id to login(adminEmail, password)
    }
}
