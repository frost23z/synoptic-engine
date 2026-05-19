package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 2 / Sprint 2c: per-record shares. Each share writes a `record_shares` row,
 * a direct `resource_visibility` row, and (for leads) cascade rows for the related
 * person/organization per `CascadeRules`.
 */
class RecordShareIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var tenantApi: TenantApi

    @Autowired
    lateinit var visibilityRepository: ResourceVisibilityRepository

    @Test
    fun `share a lead with a partner — direct visibility plus cascade to person and organization`() {
        val (ownerId, ownerToken) = provision("owner")
        val (consumerId, _) = provision("consumer")
        val (pipelineId, stageId) = defaultPipelineAndStage(ownerToken)

        // Set up a person + organization in the owner's tenant, link them to a lead.
        val personId =
            UUID.fromString(
                post(
                    "/api/contacts/persons",
                    ownerToken,
                    mapOf("firstName" to "Alice", "lastName" to "Smith"),
                ).bodyAsMap()!!["id"] as String,
            )
        val organizationId =
            UUID.fromString(
                post(
                    "/api/contacts/organizations",
                    ownerToken,
                    mapOf("name" to "Acme Inc"),
                ).bodyAsMap()!!["id"] as String,
            )
        val leadId =
            UUID.fromString(
                post(
                    "/api/leads",
                    ownerToken,
                    mapOf(
                        "title" to "Acme — ad hoc share",
                        "amount" to 25_000,
                        "pipelineId" to pipelineId,
                        "stageId" to stageId,
                        "personId" to personId.toString(),
                        "organizationId" to organizationId.toString(),
                    ),
                ).bodyAsMap()!!["id"] as String,
            )

        // Share the lead with the consumer at WRITE level.
        val shareResp =
            post(
                "/api/records/share",
                ownerToken,
                mapOf(
                    "consumerTenantId" to consumerId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "WRITE",
                ),
            )
        assertEquals(201, shareResp.status(), shareResp.response.contentAsString)
        val shareId = UUID.fromString(shareResp.bodyAsMap()!!["id"] as String)

        // Direct visibility for the lead.
        assertEquals(AccessLevel.WRITE, visibilityFor(consumerId, ResourceType.LEADS, leadId))
        // Cascade visibility for the person + organization (default READ).
        assertEquals(AccessLevel.READ, visibilityFor(consumerId, ResourceType.PERSONS, personId))
        assertEquals(AccessLevel.READ, visibilityFor(consumerId, ResourceType.ORGANIZATIONS, organizationId))

        // List shares: shows up.
        val list = get("/api/records/leads/$leadId/shares", ownerToken).bodyAsList()!!
        assertTrue(list.any { it["id"] == shareId.toString() })

        // Revoke: every visibility row attached to this share disappears.
        val revoked = delete("/api/records/share/$shareId", ownerToken)
        assertEquals(200, revoked.status())
        assertNotNull(revoked.bodyAsMap()!!["revokedAt"])

        assertEquals(AccessLevel.NONE, visibilityFor(consumerId, ResourceType.LEADS, leadId))
        assertEquals(AccessLevel.NONE, visibilityFor(consumerId, ResourceType.PERSONS, personId))
        assertEquals(AccessLevel.NONE, visibilityFor(consumerId, ResourceType.ORGANIZATIONS, organizationId))

        val listAfterRevoke = get("/api/records/leads/$leadId/shares", ownerToken).bodyAsList()!!
        assertFalse(listAfterRevoke.any { it["id"] == shareId.toString() })
    }

    @Test
    fun `cannot share a lead that isn't owned by your tenant`() {
        val (_, ownerToken) = provision("attA")
        val (otherId, otherToken) = provision("attB")
        val (otherPipeline, otherStage) = defaultPipelineAndStage(otherToken)

        // Lead lives in the OTHER tenant.
        val leadId =
            UUID.fromString(
                post(
                    "/api/leads",
                    otherToken,
                    mapOf(
                        "title" to "Their lead",
                        "amount" to 1_000,
                        "pipelineId" to otherPipeline,
                        "stageId" to otherStage,
                    ),
                ).bodyAsMap()!!["id"] as String,
            )

        val attempt =
            post(
                "/api/records/share",
                ownerToken,
                mapOf(
                    "consumerTenantId" to otherId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "READ",
                ),
            )
        // Owner tenant doesn't own the lead → 404 (we deliberately don't leak existence
        // through 403 — the lead is invisible to the acting tenant via Hibernate filter).
        assertTrue(attempt.status() == 404 || attempt.status() == 403, "Got ${attempt.status()}")
    }

    @Test
    fun `re-sharing the same record upgrades the access level instead of duplicating`() {
        val (ownerId, ownerToken) = provision("dupOwner")
        val (consumerId, _) = provision("dupConsumer")
        val (pipelineId, stageId) = defaultPipelineAndStage(ownerToken)

        val leadId =
            UUID.fromString(
                post(
                    "/api/leads",
                    ownerToken,
                    mapOf(
                        "title" to "Dup lead",
                        "amount" to 1_000,
                        "pipelineId" to pipelineId,
                        "stageId" to stageId,
                    ),
                ).bodyAsMap()!!["id"] as String,
            )

        // Share READ first.
        val first =
            post(
                "/api/records/share",
                ownerToken,
                mapOf(
                    "consumerTenantId" to consumerId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "READ",
                ),
            )
        assertEquals(201, first.status())
        val firstId = first.bodyAsMap()!!["id"]

        // Share again at WRITE — same row id returned, level upgraded.
        val second =
            post(
                "/api/records/share",
                ownerToken,
                mapOf(
                    "consumerTenantId" to consumerId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "WRITE",
                ),
            )
        assertEquals(201, second.status())
        val secondId = second.bodyAsMap()!!["id"]
        assertEquals(firstId, secondId, "Re-sharing the same record should update the existing row, not insert another")

        assertEquals(AccessLevel.WRITE, visibilityFor(consumerId, ResourceType.LEADS, leadId))
        assertNotNull(ownerId) // silence the unused
    }

    private fun visibilityFor(
        consumerTenantId: UUID,
        resourceType: ResourceType,
        resourceId: UUID,
    ): AccessLevel {
        val rows =
            visibilityRepository.findAllByConsumerTenantIdAndResourceTypeAndResourceId(
                consumerTenantId,
                resourceType.literal,
                resourceId,
            )
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
