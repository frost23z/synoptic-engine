package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmailIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list mail without token returns 401`() {
        assertEquals(401, get("/api/mail", null).status())
    }

    @Test
    fun `list mail as VIEWER returns 200`() {
        assertEquals(200, get("/api/mail", viewerToken).status())
    }

    @Test
    fun `compose email as VIEWER returns 403`() {
        assertEquals(403, post("/api/mail", viewerToken, validComposeRequest()).status())
    }

    // ── Email CRUD ────────────────────────────────────────────────────────

    @Test
    fun `compose email returns 201`() {
        val result = post("/api/mail", adminToken, validComposeRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("Test Subject", body["subject"])
        assertEquals(false, body["isRead"])
        assertTrue((body["folders"] as List<*>).contains("sent"))
    }

    @Test
    fun `compose email with blank to returns 422`() {
        assertEquals(422, post("/api/mail", adminToken, mapOf("to" to "  ", "subject" to "Test")).status())
    }

    @Test
    fun `get email by id returns detail`() {
        val id = post("/api/mail", adminToken, validComposeRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/mail/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get email by unknown id returns 404`() {
        assertEquals(404, get("/api/mail/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `list mail by folder returns filtered results`() {
        post("/api/mail", adminToken, validComposeRequest())
        val result = get("/api/mail?folder=sent", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsMap()!!["content"])
    }

    @Test
    fun `mark email as read returns updated isRead`() {
        val id = post("/api/mail", adminToken, validComposeRequest()).bodyAsMap()!!["id"] as String
        assertEquals(false, get("/api/mail/$id", adminToken).bodyAsMap()!!["isRead"])

        val result = patch("/api/mail/$id/read", adminToken, mapOf("isRead" to true))
        assertEquals(200, result.status())
        assertEquals(true, result.bodyAsMap()!!["isRead"])
    }

    @Test
    fun `move email folder returns updated folders`() {
        val id = post("/api/mail", adminToken, validComposeRequest()).bodyAsMap()!!["id"] as String
        val result = patch("/api/mail/$id/folder", adminToken, mapOf("folder" to "trash"))
        assertEquals(200, result.status())
        assertTrue((result.bodyAsMap()!!["folders"] as List<*>).contains("trash"))
    }

    @Test
    fun `delete email returns 204 and is unfindable`() {
        val id = post("/api/mail", adminToken, validComposeRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/mail/$id", adminToken).status())
        assertEquals(404, get("/api/mail/$id", adminToken).status())
    }

    // ── Lead email pivot ──────────────────────────────────────────────────

    @Test
    fun `attach email to lead returns lead with emails`() {
        val emailId = post("/api/mail", adminToken, validComposeRequest()).bodyAsMap()!!["id"] as String
        val leadId = createLead()

        val result = post("/api/leads/$leadId/emails", adminToken, mapOf("emailId" to emailId))
        assertEquals(200, result.status())
    }

    @Test
    fun `list lead emails returns attached emails`() {
        val emailId = post("/api/mail", adminToken, validComposeRequest()).bodyAsMap()!!["id"] as String
        val leadId = createLead()
        post("/api/leads/$leadId/emails", adminToken, mapOf("emailId" to emailId))

        val result = get("/api/leads/$leadId/emails", adminToken)
        assertEquals(200, result.status())
        assertEquals(1, result.bodyAsList()!!.size)
    }

    @Test
    fun `detach email from lead removes it`() {
        val emailId = post("/api/mail", adminToken, validComposeRequest()).bodyAsMap()!!["id"] as String
        val leadId = createLead()
        post("/api/leads/$leadId/emails", adminToken, mapOf("emailId" to emailId))

        val result = delete("/api/leads/$leadId/emails/$emailId", adminToken)
        assertEquals(200, result.status())
        assertEquals(0, get("/api/leads/$leadId/emails", adminToken).bodyAsList()!!.size)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validComposeRequest() =
        mapOf(
            "to" to "recipient@example.com",
            "subject" to "Test Subject",
            "body" to "Hello, this is a test email.",
            "folders" to listOf("sent"),
        )

    private fun createLead(): String =
        post(
            "/api/leads",
            adminToken,
            mapOf(
                "title" to "Lead ${UUID.randomUUID().toString().take(6)}",
                "pipelineId" to "00000000-0000-0000-0000-000000000010",
                "stageId" to "00000000-0000-0000-0000-000000000011",
            ),
        ).bodyAsMap()!!["id"] as String
}
