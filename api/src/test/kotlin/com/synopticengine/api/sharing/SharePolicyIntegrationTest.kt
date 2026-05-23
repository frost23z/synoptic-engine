package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 2 / Sprint 2a: share policy CRUD. No enforcement yet — these only verify the
 * shape of the policy table and that admin permissions are wired correctly.
 */
class SharePolicyIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Test
    fun `create list update and revoke a leads policy`() {
        val a = tenantProvisioner.provision("policyA")
        val b = tenantProvisioner.provision("policyB")
        val relId = acceptedRelationship(a, b, "PARENT_CHILD")

        val createResp =
            post(
                "/api/relationships/$relId/policies",
                a.token,
                mapOf("resourceType" to "leads", "accessLevel" to "READ", "materialize" to true),
            )
        assertEquals(201, createResp.status(), createResp.response.contentAsString)
        val policyId = createResp.bodyAsMap()!!["id"] as String
        assertEquals("READ", createResp.bodyAsMap()!!["accessLevel"])
        assertEquals("leads", createResp.bodyAsMap()!!["resourceType"])

        // Target tenant can see policies governing them but CANNOT manage them.
        assertTrue(get("/api/relationships/$relId/policies", b.token).bodyAsList()!!.any { it["id"] == policyId })
        assertEquals(403, put("/api/share-policies/$policyId", b.token, mapOf("accessLevel" to "WRITE")).status())

        // Source updates access level.
        val update = put("/api/share-policies/$policyId", a.token, mapOf("accessLevel" to "WRITE"))
        assertEquals(200, update.status(), update.response.contentAsString)
        assertEquals("WRITE", update.bodyAsMap()!!["accessLevel"])

        // Revoke: soft-restriction filters the policy out of the relationship's list.
        assertEquals(200, delete("/api/share-policies/$policyId", a.token).status())
        assertTrue(
            get("/api/relationships/$relId/policies", a.token).bodyAsList()!!.none { it["id"] == policyId },
            "Revoked policy should not appear in active list",
        )
    }

    @Test
    fun `duplicate policy for same resource returns 409`() {
        val a = tenantProvisioner.provision("dupPolA")
        val b = tenantProvisioner.provision("dupPolB")
        val relId = acceptedRelationship(a, b, "PARTNER")

        assertEquals(
            201,
            post(
                "/api/relationships/$relId/policies",
                a.token,
                mapOf("resourceType" to "leads", "accessLevel" to "READ"),
            ).status(),
        )
        assertEquals(
            409,
            post(
                "/api/relationships/$relId/policies",
                a.token,
                mapOf("resourceType" to "leads", "accessLevel" to "WRITE"),
            ).status(),
        )
    }

    @Test
    fun `unknown resource type returns 400`() {
        val a = tenantProvisioner.provision("unkA")
        val b = tenantProvisioner.provision("unkB")
        val relId = acceptedRelationship(a, b, "PARTNER")

        assertEquals(
            400,
            post(
                "/api/relationships/$relId/policies",
                a.token,
                mapOf("resourceType" to "not-a-thing", "accessLevel" to "READ"),
            ).status(),
        )
    }

    @Test
    fun `activities policy accepts materialize true`() {
        val a = tenantProvisioner.provision("actPolA")
        val b = tenantProvisioner.provision("actPolB")
        val relId = acceptedRelationship(a, b, "PARTNER")

        val createResp =
            post(
                "/api/relationships/$relId/policies",
                a.token,
                mapOf("resourceType" to "leads.activities", "accessLevel" to "READ", "materialize" to true),
            )
        assertEquals(201, createResp.status(), createResp.response.contentAsString)
    }

    private fun acceptedRelationship(
        source: TenantProvisioner.TenantAndToken,
        target: TenantProvisioner.TenantAndToken,
        type: String,
    ): String {
        val relId =
            post(
                "/api/relationships",
                source.token,
                mapOf("targetTenantId" to target.tenantId.toString(), "type" to type),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", target.token)
        return relId
    }
}
