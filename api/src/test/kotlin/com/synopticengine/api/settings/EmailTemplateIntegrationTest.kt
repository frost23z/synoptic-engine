package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmailTemplateIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    @Test
    fun `list templates without token returns 401`() {
        assertEquals(401, get("/api/settings/email-templates", null).status())
    }

    @Test
    fun `list templates as SALESPERSON returns 403`() {
        assertEquals(403, get("/api/settings/email-templates", salespersonToken).status())
    }

    @Test
    fun `create template returns 201 with isPredefined false`() {
        val body = createTemplate()
        assertNotNull(body["id"])
        assertEquals(false, body["isPredefined"])
    }

    @Test
    fun `create template with duplicate name returns 409`() {
        val name = "Template ${UUID.randomUUID().toString().take(8)}"
        createTemplate(name)
        assertEquals(
            409,
            post(
                "/api/settings/email-templates",
                adminToken,
                mapOf("name" to name, "subject" to "x", "content" to "<p>x</p>"),
            ).status(),
        )
    }

    @Test
    fun `get template by id returns detail`() {
        val id = createTemplate()["id"] as String
        val result = get("/api/settings/email-templates/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get template by unknown id returns 404`() {
        assertEquals(404, get("/api/settings/email-templates/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update template returns 200 with new subject`() {
        val id = createTemplate()["id"] as String
        val result =
            put(
                "/api/settings/email-templates/$id",
                adminToken,
                mapOf(
                    "name" to "Updated ${UUID.randomUUID().toString().take(6)}",
                    "subject" to "New Subject",
                    "content" to "<p>New</p>",
                ),
            )
        assertEquals(200, result.status())
        assertEquals("New Subject", result.bodyAsMap()!!["subject"])
    }

    @Test
    fun `delete template returns 204 and is unfindable`() {
        val id = createTemplate()["id"] as String
        assertEquals(204, delete("/api/settings/email-templates/$id", adminToken).status())
        assertEquals(404, get("/api/settings/email-templates/$id", adminToken).status())
    }

    private fun createTemplate(name: String = "Template ${UUID.randomUUID().toString().take(8)}"): Map<String, Any> =
        post(
            "/api/settings/email-templates",
            adminToken,
            mapOf("name" to name, "subject" to "Hello {{name}}", "content" to "<p>Dear {{name}}</p>"),
        ).bodyAsMap()!!
}
