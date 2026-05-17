package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebFormIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list web forms without token returns 401`() {
        assertEquals(401, get("/api/settings/web-forms", null).status())
    }

    @Test
    fun `list web forms as SALESPERSON returns 403`() {
        assertEquals(403, get("/api/settings/web-forms", salespersonToken).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create web form returns 201`() {
        val result = post("/api/settings/web-forms", adminToken, validCreateRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(true, body["isActive"])
        assertTrue((body["fields"] as List<*>).isEmpty())
    }

    @Test
    fun `create web form with fields`() {
        val attrId = createAttribute()
        val request =
            mapOf(
                "title" to "Contact Form ${UUID.randomUUID().toString().take(6)}",
                "isActive" to true,
                "fields" to listOf(mapOf("attributeId" to attrId, "sortOrder" to 1, "isRequired" to true)),
            )
        val result = post("/api/settings/web-forms", adminToken, request)
        assertEquals(201, result.status())
        val fields = result.bodyAsMap()!!["fields"] as List<*>
        assertEquals(1, fields.size)
        @Suppress("UNCHECKED_CAST")
        assertEquals(true, (fields.first() as Map<String, Any>)["isRequired"])
    }

    @Test
    fun `get web form by id returns full detail`() {
        val id = post("/api/settings/web-forms", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/settings/web-forms/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get web form by unknown id returns 404`() {
        assertEquals(404, get("/api/settings/web-forms/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update web form replaces fields`() {
        val attrId = createAttribute()
        val id = post("/api/settings/web-forms", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String

        val update =
            mapOf(
                "title" to "Updated Form",
                "isActive" to false,
                "fields" to listOf(mapOf("attributeId" to attrId, "sortOrder" to 1, "isRequired" to false)),
            )
        val result = put("/api/settings/web-forms/$id", adminToken, update)
        assertEquals(200, result.status())
        assertEquals("Updated Form", result.bodyAsMap()!!["title"])
        assertEquals(false, result.bodyAsMap()!!["isActive"])
        assertEquals(1, (result.bodyAsMap()!!["fields"] as List<*>).size)
    }

    @Test
    fun `delete web form returns 204`() {
        val id = post("/api/settings/web-forms", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/settings/web-forms/$id", adminToken).status())
        assertEquals(404, get("/api/settings/web-forms/$id", adminToken).status())
    }

    // ── Public endpoints (no auth) ────────────────────────────────────────

    @Test
    fun `public GET web form requires no authentication`() {
        val id = post("/api/settings/web-forms", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/web-forms/$id", null)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `public GET inactive web form returns 404`() {
        val id =
            post(
                "/api/settings/web-forms",
                adminToken,
                mapOf("title" to "Inactive Form", "isActive" to false),
            ).bodyAsMap()!!["id"] as String
        assertEquals(404, get("/web-forms/$id", null).status())
    }

    @Test
    fun `public submit web form returns success`() {
        val id = post("/api/settings/web-forms", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result =
            post(
                "/web-forms/$id/submit",
                null,
                mapOf("values" to mapOf("firstName" to "John", "email" to "john@test.com")),
            )
        assertEquals(200, result.status())
        assertEquals(true, result.bodyAsMap()!!["success"])
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun createAttribute(): String =
        post(
            "/api/settings/attributes",
            adminToken,
            mapOf(
                "code" to "wf_${UUID.randomUUID().toString().take(6)}",
                "adminName" to "Field",
                "type" to "TEXT",
                "entityType" to "Person",
            ),
        ).bodyAsMap()!!["id"] as String

    private fun validCreateRequest() =
        mapOf(
            "title" to "Web Form ${UUID.randomUUID().toString().take(8)}",
            "description" to "Test form",
            "isActive" to true,
        )
}
