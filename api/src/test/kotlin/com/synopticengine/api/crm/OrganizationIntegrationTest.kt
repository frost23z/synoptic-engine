package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OrganizationIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    @Test
    fun `list organizations without token returns 401`() {
        assertEquals(401, get("/api/contacts/organizations", null).status())
    }

    @Test
    fun `list organizations as SALESPERSON returns 200`() {
        assertEquals(200, get("/api/contacts/organizations", salespersonToken).status())
    }

    @Test
    fun `create organization returns 201`() {
        val result = post("/api/contacts/organizations", adminToken, validCreateRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertNotNull(body["name"])
    }

    @Test
    fun `create organization with blank name returns 422`() {
        assertEquals(422, post("/api/contacts/organizations", adminToken, mapOf("name" to "  ")).status())
    }

    @Test
    fun `create organization without token returns 401`() {
        assertEquals(401, post("/api/contacts/organizations", null, validCreateRequest()).status())
    }

    @Test
    fun `get organization by id returns detail`() {
        val id = post("/api/contacts/organizations", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/contacts/organizations/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get organization by unknown id returns 404`() {
        assertEquals(404, get("/api/contacts/organizations/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update organization returns 200`() {
        val id = post("/api/contacts/organizations", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result =
            put(
                "/api/contacts/organizations/$id",
                adminToken,
                mapOf(
                    "name" to "Updated Corp",
                    "website" to "https://updated.com",
                ),
            )
        assertEquals(200, result.status())
        assertEquals("Updated Corp", result.bodyAsMap()!!["name"])
    }

    @Test
    fun `delete organization returns 204 and is unfindable`() {
        val id = post("/api/contacts/organizations", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/contacts/organizations/$id", adminToken).status())
        assertEquals(404, get("/api/contacts/organizations/$id", adminToken).status())
    }

    @Test
    fun `delete organization requires contacts delete permission`() {
        val id = post("/api/contacts/organizations", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(403, delete("/api/contacts/organizations/$id", salespersonToken).status())
    }

    @Test
    fun `search organizations returns paginated results`() {
        val unique = "CORP${UUID.randomUUID().toString().take(6)}"
        post("/api/contacts/organizations", adminToken, mapOf("name" to "$unique Inc"))
        val result = get("/api/contacts/organizations/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["content"])
    }

    private fun validCreateRequest() =
        mapOf(
            "name" to "Acme Corp ${UUID.randomUUID().toString().take(6)}",
            "email" to "info@acme.com",
            "phone" to "+1234567890",
            "website" to "https://acme.com",
        )
}
