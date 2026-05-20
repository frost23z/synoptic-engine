package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Test
    fun `parent-child request-accept-revoke round trip`() {
        val parent = tenantProvisioner.provision("parent")
        val child = tenantProvisioner.provision("child")

        val createResp =
            post(
                "/api/relationships",
                parent.token,
                mapOf(
                    "targetTenantId" to child.tenantId.toString(),
                    "type" to "PARENT_CHILD",
                    "note" to "Holdings → regional subsidiary",
                ),
            )
        assertEquals(201, createResp.status(), createResp.response.contentAsString)
        val body = createResp.bodyAsMap()!!
        val relId = body["id"] as String
        assertEquals("PENDING", body["status"])
        assertEquals(parent.tenantId.toString(), body["sourceTenantId"])
        assertEquals(child.tenantId.toString(), body["targetTenantId"])

        // Source cannot accept their own request.
        assertEquals(403, patch("/api/relationships/$relId/accept", parent.token).status())

        // Target accepts.
        val acceptResp = patch("/api/relationships/$relId/accept", child.token)
        assertEquals(200, acceptResp.status(), acceptResp.response.contentAsString)
        assertEquals("ACTIVE", acceptResp.bodyAsMap()!!["status"])
        assertNotNull(acceptResp.bodyAsMap()!!["acceptedAt"])

        // Both ends see it in their list.
        assertTrue(get("/api/relationships", parent.token).bodyAsList()!!.any { it["id"] == relId })
        assertTrue(get("/api/relationships", child.token).bodyAsList()!!.any { it["id"] == relId })

        // Child cannot revoke a PARENT_CHILD relationship.
        assertEquals(403, patch("/api/relationships/$relId/revoke", child.token).status())

        // Parent revokes.
        val parentRevoke = patch("/api/relationships/$relId/revoke", parent.token)
        assertEquals(200, parentRevoke.status(), parentRevoke.response.contentAsString)
        assertEquals("REVOKED", parentRevoke.bodyAsMap()!!["status"])
    }

    @Test
    fun `partner relationship can be revoked by either side`() {
        val a = tenantProvisioner.provision("partnerA")
        val b = tenantProvisioner.provision("partnerB")

        val relId =
            post(
                "/api/relationships",
                a.token,
                mapOf("targetTenantId" to b.tenantId.toString(), "type" to "PARTNER"),
            ).bodyAsMap()!!["id"] as String
        assertEquals(200, patch("/api/relationships/$relId/accept", b.token).status())

        // Target side may revoke a PARTNER relationship.
        val revoke = patch("/api/relationships/$relId/revoke", b.token)
        assertEquals(200, revoke.status())
        assertEquals("REVOKED", revoke.bodyAsMap()!!["status"])
    }

    @Test
    fun `duplicate request returns 409 conflict`() {
        val a = tenantProvisioner.provision("dupA")
        val b = tenantProvisioner.provision("dupB")
        val body = mapOf("targetTenantId" to b.tenantId.toString(), "type" to "PARTNER")

        assertEquals(201, post("/api/relationships", a.token, body).status())
        assertEquals(409, post("/api/relationships", a.token, body).status())
    }

    @Test
    fun `unrelated tenant cannot see a relationship (404 to avoid leaking existence)`() {
        val a = tenantProvisioner.provision("hiddenA")
        val b = tenantProvisioner.provision("hiddenB")
        val c = tenantProvisioner.provision("hiddenC")

        val relId =
            post(
                "/api/relationships",
                a.token,
                mapOf("targetTenantId" to b.tenantId.toString(), "type" to "PARTNER"),
            ).bodyAsMap()!!["id"] as String

        assertEquals(404, get("/api/relationships/$relId", c.token).status())
        assertTrue(get("/api/relationships", c.token).bodyAsList()!!.none { it["id"] == relId })
    }

    @Test
    fun `suspend then resume returns relationship to ACTIVE`() {
        val a = tenantProvisioner.provision("suspendA")
        val b = tenantProvisioner.provision("suspendB")

        val relId =
            post(
                "/api/relationships",
                a.token,
                mapOf("targetTenantId" to b.tenantId.toString(), "type" to "PARTNER"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", b.token)

        val sus = patch("/api/relationships/$relId/suspend", a.token)
        assertEquals(200, sus.status())
        assertEquals("SUSPENDED", sus.bodyAsMap()!!["status"])

        val res = patch("/api/relationships/$relId/resume", a.token)
        assertEquals(200, res.status())
        assertEquals("ACTIVE", res.bodyAsMap()!!["status"])
    }
}
