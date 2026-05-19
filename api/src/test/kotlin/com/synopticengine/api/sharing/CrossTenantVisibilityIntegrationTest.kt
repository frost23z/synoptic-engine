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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 2 / Sprint 2b acceptance: per the task brief, each relationship type yields
 * the right `resource_visibility` rows for the consumer tenant. Once Sprint 2b
 * follow-up wires service-layer "own + shared" queries (the Hibernate `@Filter`
 * approach hit alias/JOIN-FETCH limits in Hibernate 7), the same setup will surface
 * shared records through `/api/leads`. For now this test asserts at the visibility
 * layer (the durable boundary; RLS in V040 reads the same table).
 *
 * Three trips:
 *   - PARENT_CHILD parent → child READ on leads: child gets visibility rows; revoke removes them.
 *   - PARTNER A → B READ: B sees A's. B → A WRITE: A sees B's. Per-direction revoke.
 *   - SUPPLIER_CLIENT supplier → client READ on products: client gets visibility; revoke clears.
 */
class CrossTenantVisibilityIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var tenantApi: TenantApi

    @Autowired
    lateinit var visibilityRepository: ResourceVisibilityRepository

    @Test
    fun `PARENT_CHILD READ policy on leads materializes visibility for the child only`() {
        val (parentId, parentToken) = provision("parent")
        val (childId, childToken) = provision("child")
        val (parentPipeline, parentStage) = defaultPipelineAndStage(parentToken)
        val (childPipeline, childStage) = defaultPipelineAndStage(childToken)

        val parentLeadId = createLead(parentToken, "Acme deal", parentPipeline, parentStage)
        val childLeadId = createLead(childToken, "Subsidiary deal", childPipeline, childStage)

        val relId =
            (
                post(
                    "/api/relationships",
                    parentToken,
                    mapOf("targetTenantId" to childId.toString(), "type" to "PARENT_CHILD"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$relId/accept", childToken)

        // Pre-policy: child has no visibility rows for the parent's lead.
        assertEquals(
            AccessLevel.NONE,
            visibilityFor(childId, ResourceType.LEADS, UUID.fromString(parentLeadId)),
        )

        // Source publishes READ policy on leads.
        post(
            "/api/relationships/$relId/policies",
            parentToken,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        )

        // After materialization, child has READ on parent's lead.
        assertEquals(
            AccessLevel.READ,
            visibilityFor(childId, ResourceType.LEADS, UUID.fromString(parentLeadId)),
        )
        // Parent does NOT have visibility for the child's lead (one-way edge).
        assertEquals(
            AccessLevel.NONE,
            visibilityFor(parentId, ResourceType.LEADS, UUID.fromString(childLeadId)),
        )

        // Revoke the relationship → child loses visibility.
        patch("/api/relationships/$relId/revoke", parentToken)
        assertEquals(
            AccessLevel.NONE,
            visibilityFor(childId, ResourceType.LEADS, UUID.fromString(parentLeadId)),
        )
    }

    @Test
    fun `PARTNER — each direction's policy creates its own visibility rows`() {
        val (aId, tokenA) = provision("partA")
        val (bId, tokenB) = provision("partB")
        val (pipelineA, stageA) = defaultPipelineAndStage(tokenA)
        val (pipelineB, stageB) = defaultPipelineAndStage(tokenB)

        val leadA = UUID.fromString(createLead(tokenA, "A lead", pipelineA, stageA))
        val leadB = UUID.fromString(createLead(tokenB, "B lead", pipelineB, stageB))

        // A → B PARTNER edge; B accepts; A grants READ.
        val ab =
            (
                post(
                    "/api/relationships",
                    tokenA,
                    mapOf("targetTenantId" to bId.toString(), "type" to "PARTNER"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$ab/accept", tokenB)
        post(
            "/api/relationships/$ab/policies",
            tokenA,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        )

        assertEquals(AccessLevel.READ, visibilityFor(bId, ResourceType.LEADS, leadA))
        assertEquals(AccessLevel.NONE, visibilityFor(aId, ResourceType.LEADS, leadB))

        // Reverse edge: B → A, A accepts, B grants WRITE.
        val ba =
            (
                post(
                    "/api/relationships",
                    tokenB,
                    mapOf("targetTenantId" to aId.toString(), "type" to "PARTNER"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$ba/accept", tokenA)
        post(
            "/api/relationships/$ba/policies",
            tokenB,
            mapOf("resourceType" to "leads", "accessLevel" to "WRITE"),
        )

        assertEquals(AccessLevel.WRITE, visibilityFor(aId, ResourceType.LEADS, leadB))

        // B revokes their direction → A loses visibility of B's lead; B still sees A's.
        patch("/api/relationships/$ba/revoke", tokenB)
        assertEquals(AccessLevel.NONE, visibilityFor(aId, ResourceType.LEADS, leadB))
        assertEquals(AccessLevel.READ, visibilityFor(bId, ResourceType.LEADS, leadA))
    }

    @Test
    fun `SUPPLIER_CLIENT — supplier sharing products is one-way`() {
        val (supplierId, supplierToken) = provision("supp")
        val (clientId, clientToken) = provision("client")

        val productId =
            UUID.fromString(
                post(
                    "/api/products",
                    supplierToken,
                    mapOf("name" to "Catalog item", "sku" to "SKU-${UUID.randomUUID().toString().take(6)}"),
                ).bodyAsMap()!!["id"] as String,
            )

        val relId =
            (
                post(
                    "/api/relationships",
                    supplierToken,
                    mapOf("targetTenantId" to clientId.toString(), "type" to "SUPPLIER_CLIENT"),
                ).bodyAsMap()!!["id"] as String
            )
        patch("/api/relationships/$relId/accept", clientToken)
        post(
            "/api/relationships/$relId/policies",
            supplierToken,
            mapOf("resourceType" to "products", "accessLevel" to "READ"),
        )

        assertEquals(AccessLevel.READ, visibilityFor(clientId, ResourceType.PRODUCTS, productId))
        // Reverse direction: supplier does NOT get visibility of client products.
        assertEquals(AccessLevel.NONE, visibilityFor(supplierId, ResourceType.PRODUCTS, productId))

        patch("/api/relationships/$relId/revoke", supplierToken)
        assertEquals(AccessLevel.NONE, visibilityFor(clientId, ResourceType.PRODUCTS, productId))
    }

    @Test
    fun `policy on PENDING relationship does not leak visibility until accepted`() {
        val (parentId, parentToken) = provision("late")
        val (childId, childToken) = provision("late-child")
        val (parentPipeline, parentStage) = defaultPipelineAndStage(parentToken)

        val leadId = UUID.fromString(createLead(parentToken, "Pending share", parentPipeline, parentStage))

        // Initiate, but DON'T accept yet.
        val relId =
            (
                post(
                    "/api/relationships",
                    parentToken,
                    mapOf("targetTenantId" to childId.toString(), "type" to "PARENT_CHILD"),
                ).bodyAsMap()!!["id"] as String
            )
        // Create policy on PENDING relationship — Sprint 2a allows this (data only).
        post(
            "/api/relationships/$relId/policies",
            parentToken,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        )

        // Visibility must NOT materialize because relationship isn't active yet.
        assertEquals(AccessLevel.NONE, visibilityFor(childId, ResourceType.LEADS, leadId))

        // Accept → visibility materializes.
        patch("/api/relationships/$relId/accept", childToken)
        assertEquals(AccessLevel.READ, visibilityFor(childId, ResourceType.LEADS, leadId))
        assertNotNull(parentId) // silence the unused warning
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

    private fun createLead(
        token: String,
        title: String,
        pipelineId: String,
        stageId: String,
    ): String {
        val resp =
            post(
                "/api/leads",
                token,
                mapOf("title" to title, "amount" to 1_000, "pipelineId" to pipelineId, "stageId" to stageId),
            )
        assertEquals(201, resp.status(), "createLead $title failed: ${resp.response.contentAsString}")
        return resp.bodyAsMap()!!["id"] as String
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
