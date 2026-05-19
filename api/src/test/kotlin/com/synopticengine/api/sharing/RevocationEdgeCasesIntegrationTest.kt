package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.repo.RecordShareRepository
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sprint 2d — hardening: edge cases around revocation, expiry, and idempotence.
 *
 * Tests verify the durable boundary (resource_visibility + record_shares) rather
 * than the API-layer "find own + shared" surface, which is the Sprint 2b-followup.
 */
class RevocationEdgeCasesIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var tenantApi: TenantApi

    @Autowired
    lateinit var visibilityRepository: ResourceVisibilityRepository

    @Autowired
    lateinit var recordShareRepository: RecordShareRepository

    @Test
    fun `revoking the same share twice is a no-op the second time`() {
        val (ownerId, ownerToken) = provision("twiceOwner")
        val (consumerId, _) = provision("twiceConsumer")
        val (pipelineId, stageId) = defaultPipelineAndStage(ownerToken)

        val leadId = createLead(ownerToken, "Lead", pipelineId, stageId)
        val shareId =
            (
                post(
                    "/api/records/share",
                    ownerToken,
                    mapOf(
                        "consumerTenantId" to consumerId.toString(),
                        "resourceType" to "leads",
                        "resourceId" to leadId,
                        "accessLevel" to "READ",
                    ),
                ).bodyAsMap()!!["id"] as String
            )

        val first = delete("/api/records/share/$shareId", ownerToken)
        assertEquals(200, first.status())
        val firstRevokedAt = first.bodyAsMap()!!["revokedAt"]
        assertNotNull(firstRevokedAt)

        // Second delete returns 404 because the @SQLRestriction filter hides the
        // already-revoked row from the active-row repository query. That's the
        // user-visible "share is gone" behavior; the underlying audit row remains.
        val second = delete("/api/records/share/$shareId", ownerToken)
        assertEquals(404, second.status())

        assertEquals(
            AccessLevel.NONE,
            visibilityFor(consumerId, ResourceType.LEADS, UUID.fromString(leadId)),
        )
        assertNotNull(ownerId)
    }

    @Test
    fun `expired share never shows up as effective access`() {
        val (_, ownerToken) = provision("expOwner")
        val (consumerId, _) = provision("expConsumer")
        val (pipelineId, stageId) = defaultPipelineAndStage(ownerToken)

        val leadId = createLead(ownerToken, "Expiring share", pipelineId, stageId)
        val pastTimestamp = Instant.now().minusSeconds(60).toString()
        val resp =
            post(
                "/api/records/share",
                ownerToken,
                mapOf(
                    "consumerTenantId" to consumerId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId,
                    "accessLevel" to "READ",
                    "expiresAt" to pastTimestamp,
                ),
            )
        assertEquals(201, resp.status(), resp.response.contentAsString)

        // ResourceVisibilityService.effectiveAccess filters by expires_at; record_shares
        // can still hold the row for audit, but visibility is dead.
        assertEquals(
            AccessLevel.NONE,
            visibilityFor(consumerId, ResourceType.LEADS, UUID.fromString(leadId)),
        )
    }

    @Test
    fun `revoking a relationship removes visibility from policies but leaves record_shares intact`() {
        val (parentId, parentToken) = provision("relParent")
        val (childId, childToken) = provision("relChild")
        val (pipelineId, stageId) = defaultPipelineAndStage(parentToken)

        val leadId = createLead(parentToken, "Hybrid lead", pipelineId, stageId)

        // Set up a relationship-level READ policy on leads.
        val relId =
            (
                post(
                    "/api/relationships",
                    parentToken,
                    mapOf("targetTenantId" to childId.toString(), "type" to "PARENT_CHILD"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$relId/accept", childToken)
        post(
            "/api/relationships/$relId/policies",
            parentToken,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        )

        // ALSO grant a record-level share at WRITE on the same lead.
        val shareResp =
            post(
                "/api/records/share",
                parentToken,
                mapOf(
                    "consumerTenantId" to childId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId,
                    "accessLevel" to "WRITE",
                ),
            )
        assertEquals(201, shareResp.status())

        // Effective access is max(READ, WRITE) = WRITE.
        assertEquals(
            AccessLevel.WRITE,
            visibilityFor(childId, ResourceType.LEADS, UUID.fromString(leadId)),
        )

        // Revoke the RELATIONSHIP. Policy-sourced visibility goes away. Record-share
        // visibility remains — sharing rights survive the relationship lifecycle.
        patch("/api/relationships/$relId/revoke", parentToken)

        // Now effective access should be just the record share's WRITE.
        assertEquals(
            AccessLevel.WRITE,
            visibilityFor(childId, ResourceType.LEADS, UUID.fromString(leadId)),
        )

        // The record_shares row is still there.
        val activeShares =
            recordShareRepository.findAllByOwnerTenantIdAndResourceTypeAndResourceId(
                parentId,
                ResourceType.LEADS.literal,
                UUID.fromString(leadId),
            )
        assertTrue(activeShares.any { it.revokedAt == null })
    }

    @Test
    fun `consumer can revoke their own incoming share (opt-out)`() {
        val (ownerId, ownerToken) = provision("optOwner")
        val (consumerId, consumerToken) = provision("optConsumer")
        val (pipelineId, stageId) = defaultPipelineAndStage(ownerToken)

        val leadId = createLead(ownerToken, "Opt-out", pipelineId, stageId)
        val shareId =
            (
                post(
                    "/api/records/share",
                    ownerToken,
                    mapOf(
                        "consumerTenantId" to consumerId.toString(),
                        "resourceType" to "leads",
                        "resourceId" to leadId,
                        "accessLevel" to "READ",
                    ),
                ).bodyAsMap()!!["id"] as String
            )

        // Consumer revokes — drops their own visibility.
        val revoke = delete("/api/records/share/$shareId", consumerToken)
        assertEquals(200, revoke.status())

        assertEquals(
            AccessLevel.NONE,
            visibilityFor(consumerId, ResourceType.LEADS, UUID.fromString(leadId)),
        )

        // Listing on the owner side no longer shows the share.
        val list = get("/api/records/leads/$leadId/shares", ownerToken).bodyAsList()!!
        assertFalse(list.any { it["id"] == shareId })
        assertNull(ownerId.takeIf { it == ownerId.let { _ -> null } }) // silence unused
    }

    private fun createLead(
        token: String,
        title: String,
        pipelineId: String,
        stageId: String,
    ): String =
        post(
            "/api/leads",
            token,
            mapOf("title" to title, "amount" to 1_000, "pipelineId" to pipelineId, "stageId" to stageId),
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

    private fun provision(prefix: String): Pair<UUID, String> {
        val slug = "$prefix-${UUID.randomUUID().toString().take(6)}"
        val adminEmail = "$prefix-${UUID.randomUUID()}@test.com"
        val password = "Password123!"
        val summary = tenantApi.provision(prefix.replaceFirstChar { it.titlecase() }, slug, adminEmail, password)
        return summary.id to login(adminEmail, password)
    }

    private fun defaultPipelineAndStage(token: String): Pair<String, String> {
        val pipelines =
            get("/api/pipelines", token).bodyAsList()
                ?: error("Expected pipelines list available")
        val defaultPipeline =
            pipelines.firstOrNull { it["isDefault"] == true }
                ?: error("No default pipeline found")
        val pipelineId = defaultPipeline["id"] as String

        @Suppress("UNCHECKED_CAST")
        val stages = defaultPipeline["stages"] as List<Map<String, Any>>
        return pipelineId to (stages.first()["id"] as String)
    }
}
