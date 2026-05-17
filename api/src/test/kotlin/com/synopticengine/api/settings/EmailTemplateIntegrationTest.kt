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
    fun `create template returns 201`() {
        val result = post("/api/settings/email-templates", adminToken, validCreateRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(false, body["isPredefined"])
    }

    @Test
    fun `create template with duplicate name returns 409`() {
        val request = validCreateRequest()
        post("/api/settings/email-templates", adminToken, request)
        assertEquals(409, post("/api/settings/email-templates", adminToken, request).status())
    }

    @Test
    fun `get template by id returns detail`() {
        val id = post("/api/settings/email-templates", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/settings/email-templates/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get template by unknown id returns 404`() {
        assertEquals(404, get("/api/settings/email-templates/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update template returns 200`() {
        val id = post("/api/settings/email-templates", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val update =
            mapOf(
                "name" to "Updated Name ${UUID.randomUUID().toString().take(6)}",
                "subject" to "New Subject",
                "content" to "<p>New</p>",
            )
        val result = put("/api/settings/email-templates/$id", adminToken, update)
        assertEquals(200, result.status())
        assertEquals("New Subject", result.bodyAsMap()!!["subject"])
    }

    @Test
    fun `delete template returns 204`() {
        val id = post("/api/settings/email-templates", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/settings/email-templates/$id", adminToken).status())
        assertEquals(404, get("/api/settings/email-templates/$id", adminToken).status())
    }

    private fun validCreateRequest() =
        mapOf(
            "name" to "Template ${UUID.randomUUID().toString().take(8)}",
            "subject" to "Hello {{name}}",
            "content" to "<p>Dear {{name}}, welcome!</p>",
        )
}
