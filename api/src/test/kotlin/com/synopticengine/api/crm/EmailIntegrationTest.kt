package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.LeadFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmailIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var leadFactory: LeadFactory

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

    // ── Folder catalog ────────────────────────────────────────────────────

    @Test
    fun `GET mail folders without token returns 401`() {
        assertEquals(401, get("/api/mail/folders", null).status())
    }

    @Test
    fun `GET mail folders returns all six standard folders`() {
        val result = get("/api/mail/folders", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertEquals(6, body.size)
        val folders = body.map { it["folder"] as String }.toSet()
        assertTrue(folders.containsAll(setOf("inbox", "sent", "drafts", "trash", "spam", "outbox")))
        assertNotNull(body[0]["permissionKey"])
        assertNotNull(body[0]["label"])
    }

    @Test
    fun `compose email as VIEWER returns 403`() {
        assertEquals(403, post("/api/mail", viewerToken, composeRequest()).status())
    }

    // ── Email CRUD ────────────────────────────────────────────────────────

    @Test
    fun `compose email returns 201 with sent folder and isRead false`() {
        val result = post("/api/mail", adminToken, composeRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("Test Subject", body["subject"])
        assertEquals(false, body["isRead"])
        assertTrue((body["folders"] as List<*>).contains("sent"))
        @Suppress("UNCHECKED_CAST")
        val recipients = body["to"] as List<Map<String, String>>
        assertEquals("recipient@example.com", recipients.first()["email"])
    }

    @Test
    fun `compose email with blank to returns 422`() {
        assertEquals(422, post("/api/mail", adminToken, mapOf("to" to "  ", "subject" to "Test")).status())
    }

    @Test
    fun `get email by id returns detail`() {
        val id = composeAndGetId()
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
        composeAndGetId()
        val result = get("/api/mail?folder=sent", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsMap()!!["content"])
    }

    @Test
    fun `mark email as read returns isRead true`() {
        val id = composeAndGetId()
        assertEquals(false, get("/api/mail/$id", adminToken).bodyAsMap()!!["isRead"])

        val result = patch("/api/mail/$id/read", adminToken, mapOf("isRead" to true))
        assertEquals(200, result.status())
        assertEquals(true, result.bodyAsMap()!!["isRead"])
    }

    @Test
    fun `move email folder returns updated folders`() {
        val id = composeAndGetId()
        val result = patch("/api/mail/$id/folder", adminToken, mapOf("folder" to "trash"))
        assertEquals(200, result.status())
        assertTrue((result.bodyAsMap()!!["folders"] as List<*>).contains("trash"))
    }

    @Test
    fun `delete email returns 204 and is unfindable`() {
        val id = composeAndGetId()
        assertEquals(204, delete("/api/mail/$id", adminToken).status())
        assertEquals(404, get("/api/mail/$id", adminToken).status())
    }

    @Test
    fun `reply endpoint creates a reply with Re subject`() {
        val id = composeAndGetId()
        val result = post("/api/mail/$id/reply", adminToken, mapOf("body" to "Reply body"))
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertTrue((body["subject"] as String).startsWith("Re:"))
        @Suppress("UNCHECKED_CAST")
        val recipients = body["to"] as List<Map<String, String>>
        assertTrue(recipients.isNotEmpty())
    }

    // ── Lead email pivot ──────────────────────────────────────────────────

    @Test
    fun `attach detach and list emails on lead`() {
        val emailId = composeAndGetId()
        val leadId = leadFactory.id(adminToken)

        val attach = post("/api/leads/$leadId/emails", adminToken, mapOf("emailId" to emailId))
        assertEquals(200, attach.status())
        assertEquals(1, get("/api/leads/$leadId/emails", adminToken).bodyAsList()!!.size)

        val detach = delete("/api/leads/$leadId/emails/$emailId", adminToken)
        assertEquals(200, detach.status())
        assertEquals(0, get("/api/leads/$leadId/emails", adminToken).bodyAsList()!!.size)
    }

    private fun composeRequest() =
        mapOf(
            "to" to "recipient@example.com",
            "subject" to "Test Subject",
            "body" to "Hello, this is a test email.",
            "folders" to listOf("sent"),
        )

    private fun composeAndGetId(): String =
        post("/api/mail", adminToken, composeRequest()).bodyAsMap()!!["id"] as String
}
