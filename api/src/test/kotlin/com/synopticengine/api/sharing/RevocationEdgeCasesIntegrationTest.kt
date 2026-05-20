package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.repo.RecordShareRepository
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Sprint 2d — hardening: edge cases around revocation, expiry, and idempotence.
 *
 * Tests verify the durable boundary (resource_visibility + record_shares) rather
 * than the API-layer "find own + shared" surface, which is the Sprint 2b-followup.
 */
class RevocationEdgeCasesIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var visibilityRepository: ResourceVisibilityRepository

    @Autowired private lateinit var recordShareRepository: RecordShareRepository

    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    @Test
    fun `revoking the same share twice returns 404 on second attempt`() {
        val owner = tenantProvisioner.provision("twiceOwner")
        val consumer = tenantProvisioner.provision("twiceConsumer")
        val leadId = leadFactory.id(owner.token, title = "Lead")
        val shareId = createShare(owner.token, consumer.tenantId, leadId, "READ")

        val first = delete("/api/records/share/$shareId", owner.token)
        assertEquals(200, first.status())
        assertNotNull(first.bodyAsMap()!!["revokedAt"])

        // @SQLRestriction filters the revoked row from the active-row repository query.
        assertEquals(404, delete("/api/records/share/$shareId", owner.token).status())

        assertEquals(AccessLevel.NONE, visibilityFor(consumer.tenantId, ResourceType.LEADS, leadId))
    }

    @Test
    fun `expired share never shows up as effective access`() {
        val owner = tenantProvisioner.provision("expOwner")
        val consumer = tenantProvisioner.provision("expConsumer")
        val leadId = leadFactory.id(owner.token, title = "Expiring share")

        val resp =
            post(
                "/api/records/share",
                owner.token,
                mapOf(
                    "consumerTenantId" to consumer.tenantId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "READ",
                    "expiresAt" to Instant.now().minusSeconds(60).toString(),
                ),
            )
        assertEquals(201, resp.status(), resp.response.contentAsString)

        // ResourceVisibilityService.effectiveAccess filters by expires_at; the record
        // can still hold the row for audit, but visibility is dead.
        assertEquals(AccessLevel.NONE, visibilityFor(consumer.tenantId, ResourceType.LEADS, leadId))
    }

    @Test
    fun `revoking a relationship removes policy visibility but leaves record_shares intact`() {
        val parent = tenantProvisioner.provision("relParent")
        val child = tenantProvisioner.provision("relChild")
        val leadId = leadFactory.id(parent.token, title = "Hybrid lead")

        // Relationship-level READ policy on leads.
        val relId =
            post(
                "/api/relationships",
                parent.token,
                mapOf("targetTenantId" to child.tenantId.toString(), "type" to "PARENT_CHILD"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", child.token)
        post(
            "/api/relationships/$relId/policies",
            parent.token,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        )

        // ALSO grant a record-level WRITE share on the same lead.
        createShare(parent.token, child.tenantId, leadId, "WRITE")

        // Effective access is max(READ, WRITE) = WRITE.
        assertEquals(AccessLevel.WRITE, visibilityFor(child.tenantId, ResourceType.LEADS, leadId))

        // Revoke the RELATIONSHIP — policy visibility goes away, record-share remains.
        patch("/api/relationships/$relId/revoke", parent.token)

        assertEquals(AccessLevel.WRITE, visibilityFor(child.tenantId, ResourceType.LEADS, leadId))
        val activeShares =
            recordShareRepository.findAllByOwnerTenantIdAndResourceTypeAndResourceId(
                parent.tenantId,
                ResourceType.LEADS.literal,
                leadId,
            )
        assertTrue(activeShares.any { it.revokedAt == null })
    }

    @Test
    fun `consumer can revoke their own incoming share (opt-out)`() {
        val owner = tenantProvisioner.provision("optOwner")
        val consumer = tenantProvisioner.provision("optConsumer")
        val leadId = leadFactory.id(owner.token, title = "Opt-out")
        val shareId = createShare(owner.token, consumer.tenantId, leadId, "READ")

        val revoke = delete("/api/records/share/$shareId", consumer.token)
        assertEquals(200, revoke.status())

        assertEquals(AccessLevel.NONE, visibilityFor(consumer.tenantId, ResourceType.LEADS, leadId))
        assertFalse(get("/api/records/leads/$leadId/shares", owner.token).bodyAsList()!!.any { it["id"] == shareId })
    }

    private fun createShare(
        ownerToken: String,
        consumerTenantId: UUID,
        leadId: UUID,
        accessLevel: String,
    ): String =
        post(
            "/api/records/share",
            ownerToken,
            mapOf(
                "consumerTenantId" to consumerTenantId.toString(),
                "resourceType" to "leads",
                "resourceId" to leadId.toString(),
                "accessLevel" to accessLevel,
            ),
        ).bodyAsMap()!!["id"] as String

    private fun visibilityFor(
        consumerTenantId: UUID,
        resourceType: ResourceType,
        resourceId: UUID,
    ): AccessLevel {
        val rows =
            visibilityRepository
                .findAllByConsumerTenantIdAndResourceTypeAndResourceId(
                    consumerTenantId,
                    resourceType.literal,
                    resourceId,
                ).filter { it.expiresAt == null || it.expiresAt!! > Instant.now() }
        if (rows.isEmpty()) return AccessLevel.NONE
        return rows.map { it.accessLevel }.reduce(AccessLevel::max)
    }
}
