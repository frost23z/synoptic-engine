package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ActivityFactory
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.OrganizationFactory
import com.synopticengine.api.support.factories.PersonFactory
import com.synopticengine.api.support.factories.ProductFactory
import com.synopticengine.api.support.factories.QuoteFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import com.synopticengine.api.support.factories.WarehouseFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

/**
 * T3.1 — IDOR (Insecure Direct Object Reference) cross-entity regression.
 *
 * Creates resources in Tenant A, then asserts Tenant B's token cannot fetch
 * them via the GET-by-id endpoints. Acceptable responses are 404 (entity
 * invisible due to JPQL tenant filter) or 403 (explicit `requireOwnership()`
 * check tripped). Either proves the isolation layers hold.
 *
 * Coverage: leads, persons, organizations, quotes, activities, products,
 * warehouses. Webhooks and system-configs are covered by service-layer JPQL
 * filters (findActiveById / findByCode) and are omitted here because they
 * require additional setup not worth duplicating in this suite.
 */
class IDORCrossEntityIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var personFactory: PersonFactory

    @Autowired private lateinit var organizationFactory: OrganizationFactory

    @Autowired private lateinit var activityFactory: ActivityFactory

    @Autowired private lateinit var productFactory: ProductFactory

    @Autowired private lateinit var warehouseFactory: WarehouseFactory

    @Autowired private lateinit var quoteFactory: QuoteFactory

    @Test
    fun `tenant B cannot GET tenant A's lead by id`() {
        val a = tenantProvisioner.provision("idor-lead-a")
        val b = tenantProvisioner.provision("idor-lead-b")
        val id = leadFactory.create(a.token, title = "A-lead")["id"] as String
        assertDenied(b.token, "/api/leads/$id", "lead")
    }

    @Test
    fun `tenant B cannot GET tenant A's person by id`() {
        val a = tenantProvisioner.provision("idor-person-a")
        val b = tenantProvisioner.provision("idor-person-b")
        val id = personFactory.create(a.token, firstName = "Alice", lastName = "A")["id"] as String
        assertDenied(b.token, "/api/contacts/persons/$id", "person")
    }

    @Test
    fun `tenant B cannot GET tenant A's organization by id`() {
        val a = tenantProvisioner.provision("idor-org-a")
        val b = tenantProvisioner.provision("idor-org-b")
        val id = organizationFactory.create(a.token, name = "Org-A")["id"] as String
        assertDenied(b.token, "/api/contacts/organizations/$id", "organization")
    }

    @Test
    fun `tenant B cannot GET tenant A's quote by id`() {
        val a = tenantProvisioner.provision("idor-quote-a")
        val b = tenantProvisioner.provision("idor-quote-b")
        val id = quoteFactory.create(a.token)["id"] as String
        assertDenied(b.token, "/api/quotes/$id", "quote")
    }

    @Test
    fun `tenant B cannot GET tenant A's activity by id`() {
        val a = tenantProvisioner.provision("idor-activity-a")
        val b = tenantProvisioner.provision("idor-activity-b")
        val id = activityFactory.create(a.token, title = "Meet-A")["id"] as String
        assertDenied(b.token, "/api/activities/$id", "activity")
    }

    @Test
    fun `tenant B cannot GET tenant A's product by id`() {
        val a = tenantProvisioner.provision("idor-product-a")
        val b = tenantProvisioner.provision("idor-product-b")
        val id = productFactory.create(a.token, name = "Prod-A")["id"] as String
        assertDenied(b.token, "/api/products/$id", "product")
    }

    @Test
    fun `tenant B cannot GET tenant A's warehouse by id`() {
        val a = tenantProvisioner.provision("idor-wh-a")
        val b = tenantProvisioner.provision("idor-wh-b")
        val id = warehouseFactory.create(a.token, name = "WH-A")["id"] as String
        assertDenied(b.token, "/api/warehouses/$id", "warehouse")
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun assertDenied(
        token: String,
        path: String,
        entityName: String,
    ) {
        val result = get(path, token)
        val status = result.response.status
        assertTrue(
            status == 404 || status == 403,
            "Tenant B fetching $entityName from Tenant A should return 404 or 403, got $status",
        )
    }
}
