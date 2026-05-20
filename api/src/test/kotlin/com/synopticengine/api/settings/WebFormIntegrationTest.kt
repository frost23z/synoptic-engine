package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.AttributeFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebFormIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var attributeFactory: AttributeFactory

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
    fun `create web form returns 201 with empty fields and isActive true`() {
        val body = createForm()
        assertNotNull(body["id"])
        assertEquals(true, body["isActive"])
        assertTrue((body["fields"] as List<*>).isEmpty())
    }

    @Test
    fun `create web form with fields persists isRequired`() {
        val attrId = attributeFactory.id(adminToken, entityType = "Person")
        val body =
            post(
                "/api/settings/web-forms",
                adminToken,
                mapOf(
                    "title" to "Contact Form ${UUID.randomUUID().toString().take(6)}",
                    "isActive" to true,
                    "fields" to
                        listOf(mapOf("attributeId" to attrId.toString(), "sortOrder" to 1, "isRequired" to true)),
                ),
            ).bodyAsMap()!!
        val fields = body["fields"] as List<*>
        assertEquals(1, fields.size)
        @Suppress("UNCHECKED_CAST")
        assertEquals(true, (fields.first() as Map<String, Any>)["isRequired"])
    }

    @Test
    fun `get web form by id returns full detail`() {
        val id = createForm()["id"] as String
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
        val attrId = attributeFactory.id(adminToken, entityType = "Person")
        val id = createForm()["id"] as String

        val result =
            put(
                "/api/settings/web-forms/$id",
                adminToken,
                mapOf(
                    "title" to "Updated Form",
                    "isActive" to false,
                    "fields" to
                        listOf(mapOf("attributeId" to attrId.toString(), "sortOrder" to 1, "isRequired" to false)),
                ),
            )
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals("Updated Form", body["title"])
        assertEquals(false, body["isActive"])
        assertEquals(1, (body["fields"] as List<*>).size)
    }

    @Test
    fun `delete web form returns 204 and is unfindable`() {
        val id = createForm()["id"] as String
        assertEquals(204, delete("/api/settings/web-forms/$id", adminToken).status())
        assertEquals(404, get("/api/settings/web-forms/$id", adminToken).status())
    }

    // ── Public endpoints (no auth) ────────────────────────────────────────

    @Test
    fun `public GET web form requires no authentication`() {
        val id = createForm()["id"] as String
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
        val id = createForm()["id"] as String
        val result =
            post(
                "/web-forms/$id/submit",
                null,
                mapOf("values" to mapOf("firstName" to "John", "email" to "john@test.com")),
            )
        assertEquals(200, result.status())
        assertEquals(true, result.bodyAsMap()!!["success"])
    }

    private fun createForm(): Map<String, Any> =
        post(
            "/api/settings/web-forms",
            adminToken,
            mapOf(
                "title" to "Web Form ${UUID.randomUUID().toString().take(8)}",
                "description" to "Test form",
                "isActive" to true,
            ),
        ).bodyAsMap()!!
}
