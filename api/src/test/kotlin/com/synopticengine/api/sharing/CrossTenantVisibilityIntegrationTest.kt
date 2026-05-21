package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.ProductFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals

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
    @Autowired private lateinit var visibilityRepository: ResourceVisibilityRepository

    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var productFactory: ProductFactory

    @Test
    fun `PARENT_CHILD READ policy on leads materializes visibility for the child only`() {
        val parent = tenantProvisioner.provision("parent")
        val child = tenantProvisioner.provision("child")
        val parentLeadId = leadFactory.id(parent.token, title = "Acme deal")
        val childLeadId = leadFactory.id(child.token, title = "Subsidiary deal")

        val relId =
            post(
                "/api/relationships",
                parent.token,
                mapOf("targetTenantId" to child.tenantId.toString(), "type" to "PARENT_CHILD"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", child.token)

        // Pre-policy: child has no visibility for the parent's lead.
        assertEquals(AccessLevel.NONE, visibilityFor(child.tenantId, ResourceType.LEADS, parentLeadId))

        post(
            "/api/relationships/$relId/policies",
            parent.token,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        )

        // After materialization: child gets READ on parent's lead, parent does NOT see child's lead.
        assertEquals(AccessLevel.READ, visibilityFor(child.tenantId, ResourceType.LEADS, parentLeadId))
        assertEquals(AccessLevel.NONE, visibilityFor(parent.tenantId, ResourceType.LEADS, childLeadId))

        // Revoke the relationship → child loses visibility.
        patch("/api/relationships/$relId/revoke", parent.token)
        assertEquals(AccessLevel.NONE, visibilityFor(child.tenantId, ResourceType.LEADS, parentLeadId))
    }

    @Test
    fun `PARTNER — each direction's policy creates its own visibility rows`() {
        val a = tenantProvisioner.provision("partA")
        val b = tenantProvisioner.provision("partB")
        val leadA = leadFactory.id(a.token, title = "A lead")
        val leadB = leadFactory.id(b.token, title = "B lead")

        // A → B PARTNER edge; B accepts; A grants READ.
        val ab =
            post(
                "/api/relationships",
                a.token,
                mapOf("targetTenantId" to b.tenantId.toString(), "type" to "PARTNER"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$ab/accept", b.token)
        post("/api/relationships/$ab/policies", a.token, mapOf("resourceType" to "leads", "accessLevel" to "READ"))

        assertEquals(AccessLevel.READ, visibilityFor(b.tenantId, ResourceType.LEADS, leadA))
        assertEquals(AccessLevel.NONE, visibilityFor(a.tenantId, ResourceType.LEADS, leadB))

        // Reverse edge: B → A, A accepts, B grants WRITE.
        val ba =
            post(
                "/api/relationships",
                b.token,
                mapOf("targetTenantId" to a.tenantId.toString(), "type" to "PARTNER"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$ba/accept", a.token)
        post("/api/relationships/$ba/policies", b.token, mapOf("resourceType" to "leads", "accessLevel" to "WRITE"))

        assertEquals(AccessLevel.WRITE, visibilityFor(a.tenantId, ResourceType.LEADS, leadB))

        // B revokes their direction → A loses visibility of B's lead; B still sees A's.
        patch("/api/relationships/$ba/revoke", b.token)
        assertEquals(AccessLevel.NONE, visibilityFor(a.tenantId, ResourceType.LEADS, leadB))
        assertEquals(AccessLevel.READ, visibilityFor(b.tenantId, ResourceType.LEADS, leadA))
    }

    @Test
    fun `SUPPLIER_CLIENT — supplier sharing products is one-way`() {
        val supplier = tenantProvisioner.provision("supp")
        val client = tenantProvisioner.provision("client")
        val productId = productFactory.id(supplier.token, name = "Catalog item")

        val relId =
            post(
                "/api/relationships",
                supplier.token,
                mapOf("targetTenantId" to client.tenantId.toString(), "type" to "SUPPLIER_CLIENT"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", client.token)
        post(
            "/api/relationships/$relId/policies",
            supplier.token,
            mapOf("resourceType" to "products", "accessLevel" to "READ"),
        )

        assertEquals(AccessLevel.READ, visibilityFor(client.tenantId, ResourceType.PRODUCTS, productId))
        // Reverse direction: supplier does NOT get visibility of client products.
        assertEquals(AccessLevel.NONE, visibilityFor(supplier.tenantId, ResourceType.PRODUCTS, productId))

        patch("/api/relationships/$relId/revoke", supplier.token)
        assertEquals(AccessLevel.NONE, visibilityFor(client.tenantId, ResourceType.PRODUCTS, productId))
    }

    @Test
    fun `policy on PENDING relationship does not leak visibility until accepted`() {
        val parent = tenantProvisioner.provision("late")
        val child = tenantProvisioner.provision("late-child")
        val leadId = leadFactory.id(parent.token, title = "Pending share")

        // Initiate but don't accept yet — Sprint 2a allows policy data on PENDING relationships.
        val relId =
            post(
                "/api/relationships",
                parent.token,
                mapOf("targetTenantId" to child.tenantId.toString(), "type" to "PARENT_CHILD"),
            ).bodyAsMap()!!["id"] as String
        post(
            "/api/relationships/$relId/policies",
            parent.token,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        )

        // Visibility must NOT materialize because the relationship isn't active yet.
        assertEquals(AccessLevel.NONE, visibilityFor(child.tenantId, ResourceType.LEADS, leadId))

        // Accept → visibility materializes.
        patch("/api/relationships/$relId/accept", child.token)
        assertEquals(AccessLevel.READ, visibilityFor(child.tenantId, ResourceType.LEADS, leadId))
    }

    @Test
    fun `policy filterJson materializes only matching resources`() {
        val source = tenantProvisioner.provision("filter-src")
        val target = tenantProvisioner.provision("filter-tgt")
        val visibleLead = leadFactory.id(source.token, title = "Visible lead")
        val hiddenLead = leadFactory.id(source.token, title = "Hidden lead")

        val relId =
            post(
                "/api/relationships",
                source.token,
                mapOf("targetTenantId" to target.tenantId.toString(), "type" to "PARENT_CHILD"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", target.token)

        val create =
            post(
                "/api/relationships/$relId/policies",
                source.token,
                mapOf(
                    "resourceType" to "leads",
                    "accessLevel" to "READ",
                    "filterJson" to """{"title":"Visible lead"}""",
                ),
            )
        assertEquals(201, create.status(), create.response.contentAsString)

        assertEquals(AccessLevel.READ, visibilityFor(target.tenantId, ResourceType.LEADS, visibleLead))
        assertEquals(AccessLevel.NONE, visibilityFor(target.tenantId, ResourceType.LEADS, hiddenLead))
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
}
