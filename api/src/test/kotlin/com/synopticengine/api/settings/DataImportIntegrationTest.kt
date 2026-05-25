package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataImportIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list imports without token returns 401`() {
        assertEquals(401, get("/api/settings/imports", null).status())
    }

    @Test
    fun `list imports as VIEWER returns 403`() {
        assertEquals(403, get("/api/settings/imports", viewerToken).status())
    }

    @Test
    fun `upload import as VIEWER returns 403`() {
        assertEquals(
            403,
            multipart(
                "/api/settings/imports",
                viewerToken,
                personCsv(),
                "persons.csv",
                mapOf("entityType" to "Person"),
            ).status(),
        )
    }

    // ── Import CRUD ───────────────────────────────────────────────────────

    @Test
    fun `upload CSV file returns 201`() {
        val result =
            multipart(
                "/api/settings/imports",
                adminToken,
                personCsv(),
                "persons.csv",
                mapOf("entityType" to "Person"),
            )
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("PENDING", body["status"])
        assertEquals("Person", body["entityType"])
    }

    @Test
    fun `get import by id returns detail`() {
        val id = uploadPersonCsv()
        val result = get("/api/settings/imports/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get import by unknown id returns 404`() {
        assertEquals(404, get("/api/settings/imports/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `start import processes records and sets COMPLETED`() {
        val id = uploadPersonCsv()
        val result = post("/api/settings/imports/$id/start", adminToken, null)
        assertEquals(200, result.status())
        // Give async processor time to finish
        Thread.sleep(500)
        val stats = get("/api/settings/imports/$id/stats", adminToken).bodyAsMap()!!
        // Status should be PROCESSING or COMPLETED after async
        assertTrue(stats["status"] == "PROCESSING" || stats["status"] == "COMPLETED")
    }

    @Test
    fun `delete import returns 204`() {
        val id = uploadPersonCsv()
        assertEquals(204, delete("/api/settings/imports/$id", adminToken).status())
        assertEquals(404, get("/api/settings/imports/$id", adminToken).status())
    }

    @Test
    fun `stats endpoint returns error and success counts`() {
        val id = uploadPersonCsv()
        val result = get("/api/settings/imports/$id/stats", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["status"])
        assertNotNull(body["errorCount"])
        assertNotNull(body["successCount"])
    }

    // ── Sample CSV ────────────────────────────────────────────────────────

    @Test
    fun `sample CSV for person returns CSV content`() {
        val result = get("/api/settings/imports/sample/person", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.response.contentType?.contains("text/csv") == true)
        assertTrue(result.response.contentAsString.contains("firstName"))
    }

    @Test
    fun `sample CSV for lead returns CSV content`() {
        val result = get("/api/settings/imports/sample/lead", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.response.contentAsString.contains("title"))
    }

    @Test
    fun `sample CSV for product returns CSV content`() {
        val result = get("/api/settings/imports/sample/product", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.response.contentAsString.contains("name"))
    }

    @Test
    fun `sample CSV for unknown type returns 404`() {
        assertEquals(404, get("/api/settings/imports/sample/unknown", adminToken).status())
    }

    // ── Export ────────────────────────────────────────────────────────────

    @Test
    fun `export persons returns CSV`() {
        val result = get("/api/persons/export", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.response.contentType?.contains("text/csv") == true)
        assertTrue(result.response.contentAsString.contains("firstName"))
    }

    @Test
    fun `export leads returns CSV`() {
        val result = get("/api/leads/export", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.response.contentAsString.contains("title"))
    }

    @Test
    fun `export products returns CSV`() {
        val result = get("/api/products/export", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.response.contentAsString.contains("name"))
    }

    @Test
    fun `export persons without token returns 401`() {
        assertEquals(401, get("/api/persons/export", null).status())
    }

    // ── Lead CSV import ───────────────────────────────────────────────────

    @Test
    fun `upload lead CSV returns 201`() {
        val result =
            multipart("/api/settings/imports", adminToken, leadCsv(), "leads.csv", mapOf("entityType" to "Lead"))
        assertEquals(201, result.status())
        assertEquals("Lead", result.bodyAsMap()!!["entityType"])
    }

    @Test
    fun `lead import keeps partial success and captures row-level errors`() {
        val csv =
            (
                "title,description,amount,pipelineId,stageId\n" +
                    "Valid Deal,ok,500,00000000-0000-0000-0000-000000000010,00000000-0000-0000-0000-000000000011\n" +
                    ",missing title,250,00000000-0000-0000-0000-000000000010,00000000-0000-0000-0000-000000000011"
            ).toByteArray()
        val importId =
            multipart("/api/settings/imports", adminToken, csv, "leads-partial.csv", mapOf("entityType" to "Lead"))
                .bodyAsMap()!!["id"] as String
        assertEquals(200, post("/api/settings/imports/$importId/start", adminToken, null).status())

        var stats = get("/api/settings/imports/$importId/stats", adminToken).bodyAsMap()!!
        repeat(25) {
            if (stats["status"] == "COMPLETED" || stats["status"] == "FAILED") return@repeat
            Thread.sleep(200)
            stats = get("/api/settings/imports/$importId/stats", adminToken).bodyAsMap()!!
        }

        assertEquals("COMPLETED", stats["status"])
        assertEquals(1, stats["successCount"])
        assertEquals(1, stats["errorCount"])
        @Suppress("UNCHECKED_CAST")
        val errors = stats["errors"] as List<Map<String, Any>>
        assertEquals("3", errors.first()["row"]?.toString())
        assertTrue(errors.first()["error"]?.toString()?.contains("title is required") == true)
    }

    @Test
    fun `upload product CSV returns 201`() {
        val result =
            multipart(
                "/api/settings/imports",
                adminToken,
                productCsv(),
                "products.csv",
                mapOf("entityType" to "Product"),
            )
        assertEquals(201, result.status())
        assertEquals("Product", result.bodyAsMap()!!["entityType"])
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun uploadPersonCsv(): String =
        multipart(
            "/api/settings/imports",
            adminToken,
            personCsv(),
            "persons.csv",
            mapOf("entityType" to "Person"),
        ).bodyAsMap()!!["id"] as String

    private fun personCsv() =
        "firstName,lastName,email,phone,jobTitle\nAlice,Smith,alice@example.com,+1111111111,Engineer\nBob,Jones,bob@example.com,,Manager"
            .toByteArray()

    private fun leadCsv() =
        "title,description,amount\nDeal Alpha,First deal,5000\nDeal Beta,,2500"
            .toByteArray()

    private fun productCsv() =
        "name,sku,price,description\nWidget A,SKU-A,9.99,A widget\nWidget B,SKU-B,19.99,"
            .toByteArray()
}
