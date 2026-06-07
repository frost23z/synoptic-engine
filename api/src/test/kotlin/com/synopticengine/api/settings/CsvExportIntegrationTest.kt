package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.OrganizationFactory
import com.synopticengine.api.support.factories.PersonFactory
import com.synopticengine.api.support.factories.ProductFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers `CsvExportController` (`/api/{persons,organizations,leads,products}/export`),
 * which backs the "Export" button on each list page. Each export streams a CSV with a
 * fixed header row; the tests assert the auth guard, the `text/csv` content type, the
 * `attachment` disposition, and that a freshly-created record shows up in the output.
 */
class CsvExportIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var personFactory: PersonFactory

    @Autowired private lateinit var organizationFactory: OrganizationFactory

    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var productFactory: ProductFactory

    private lateinit var adminToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    private fun assertCsvResponse(
        path: String,
        expectedHeader: String,
        expectedFilename: String,
    ): String {
        val result = get(path, adminToken)
        assertEquals(200, result.status(), result.response.contentAsString)
        assertTrue(
            result.response.contentType?.startsWith("text/csv") == true,
            "expected text/csv content type, got ${result.response.contentType}",
        )
        assertTrue(
            result.response.getHeader("Content-Disposition")?.contains(expectedFilename) == true,
            "expected attachment filename $expectedFilename in Content-Disposition",
        )
        val body = result.response.contentAsString
        assertTrue(body.startsWith(expectedHeader), "expected CSV header '$expectedHeader', got: ${body.take(120)}")
        return body
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `exports without a token return 401`() {
        assertEquals(401, get("/api/persons/export", null).status())
        assertEquals(401, get("/api/organizations/export", null).status())
        assertEquals(401, get("/api/leads/export", null).status())
        assertEquals(401, get("/api/products/export", null).status())
    }

    // ── Person / organization / lead / product exports ────────────────────

    @Test
    fun `persons export includes a freshly created person`() {
        val firstName = "Export${UUID.randomUUID().toString().take(6)}"
        personFactory.create(adminToken, firstName = firstName, lastName = "Csv")
        val body = assertCsvResponse("/api/persons/export", "id,firstName,lastName,email,phone,jobTitle", "persons.csv")
        assertTrue(body.contains(firstName), "exported CSV should contain $firstName")
    }

    @Test
    fun `organizations export includes a freshly created organization`() {
        val name = "ExportOrg-${UUID.randomUUID().toString().take(8)}"
        organizationFactory.create(adminToken, name = name)
        val body =
            assertCsvResponse("/api/organizations/export", "id,name,email,phone,website,address", "organizations.csv")
        assertTrue(body.contains(name), "exported CSV should contain $name")
    }

    @Test
    fun `leads export includes a freshly created lead`() {
        val title = "ExportLead-${UUID.randomUUID().toString().take(8)}"
        leadFactory.create(adminToken, title = title)
        val body = assertCsvResponse("/api/leads/export", "id,title,status,amount,pipelineId,stageId", "leads.csv")
        assertTrue(body.contains(title), "exported CSV should contain $title")
    }

    @Test
    fun `products export includes a freshly created product`() {
        val name = "ExportProduct-${UUID.randomUUID().toString().take(8)}"
        productFactory.create(adminToken, name = name)
        val body = assertCsvResponse("/api/products/export", "id,name,sku,price,description", "products.csv")
        assertTrue(body.contains(name), "exported CSV should contain $name")
    }
}
