package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttributeIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list attributes without token returns 401`() {
        assertEquals(401, get("/api/settings/attributes", null).status())
    }

    @Test
    fun `list attributes as SALESPERSON returns 403`() {
        assertEquals(403, get("/api/settings/attributes", salespersonToken).status())
    }

    @Test
    fun `create attribute as SALESPERSON returns 403`() {
        assertEquals(403, post("/api/settings/attributes", salespersonToken, validCreateRequest("Lead")).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create attribute returns 201 with correct fields`() {
        val request = validCreateRequest("Lead")
        val result = post("/api/settings/attributes", adminToken, request)
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(request["code"], body["code"])
        assertEquals("Lead", body["entityType"])
        assertEquals("TEXT", body["type"])
        assertTrue((body["options"] as List<*>).isEmpty())
    }

    @Test
    fun `create duplicate code for same entityType returns 409`() {
        val request = validCreateRequest("Lead")
        post("/api/settings/attributes", adminToken, request)
        assertEquals(409, post("/api/settings/attributes", adminToken, request).status())
    }

    @Test
    fun `same code for different entityType is allowed`() {
        val code = "code_${UUID.randomUUID().toString().take(6)}"
        post(
            "/api/settings/attributes",
            adminToken,
            mapOf(
                "code" to code,
                "adminName" to "X",
                "type" to "TEXT",
                "entityType" to "Lead",
            ),
        )
        val result =
            post(
                "/api/settings/attributes",
                adminToken,
                mapOf(
                    "code" to code,
                    "adminName" to "X",
                    "type" to "TEXT",
                    "entityType" to "Person",
                ),
            )
        assertEquals(201, result.status())
    }

    @Test
    fun `get attribute by id returns detail with options`() {
        val id = post("/api/settings/attributes", adminToken, validCreateRequest("Lead")).bodyAsMap()!!["id"] as String
        val result = get("/api/settings/attributes/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `filter attributes by entityType`() {
        val code = "filter_${UUID.randomUUID().toString().take(6)}"
        post(
            "/api/settings/attributes",
            adminToken,
            mapOf(
                "code" to code,
                "adminName" to "X",
                "type" to "TEXT",
                "entityType" to "Organization",
            ),
        )
        val result = get("/api/settings/attributes?entityType=Organization", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertTrue(body.all { it["entityType"] == "Organization" })
    }

    @Test
    fun `update attribute returns 200`() {
        val id = post("/api/settings/attributes", adminToken, validCreateRequest("Lead")).bodyAsMap()!!["id"] as String
        val result =
            put(
                "/api/settings/attributes/$id",
                adminToken,
                mapOf(
                    "adminName" to "Updated Name",
                    "type" to "TEXTAREA",
                    "sortOrder" to 5,
                ),
            )
        assertEquals(200, result.status())
        assertEquals("Updated Name", result.bodyAsMap()!!["adminName"])
        assertEquals("TEXTAREA", result.bodyAsMap()!!["type"])
    }

    @Test
    fun `delete attribute returns 204`() {
        val id = post("/api/settings/attributes", adminToken, validCreateRequest("Lead")).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/settings/attributes/$id", adminToken).status())
        assertEquals(404, get("/api/settings/attributes/$id", adminToken).status())
    }

    // ── Options ───────────────────────────────────────────────────────────

    @Test
    fun `add option to SELECT attribute`() {
        val id =
            post(
                "/api/settings/attributes",
                adminToken,
                mapOf(
                    "code" to "sel_${UUID.randomUUID().toString().take(6)}",
                    "adminName" to "Status",
                    "type" to "SELECT",
                    "entityType" to "Lead",
                ),
            ).bodyAsMap()!!["id"] as String

        val result =
            post(
                "/api/settings/attributes/$id/options",
                adminToken,
                mapOf(
                    "adminName" to "Hot",
                    "sortOrder" to 1,
                ),
            )
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("Hot", body["adminName"])

        // verify it appears in the attribute
        val attr = get("/api/settings/attributes/$id", adminToken).bodyAsMap()!!
        assertEquals(1, (attr["options"] as List<*>).size)
    }

    @Test
    fun `update option returns 200`() {
        val attrId =
            post(
                "/api/settings/attributes",
                adminToken,
                mapOf(
                    "code" to "o_${UUID.randomUUID().toString().take(6)}",
                    "adminName" to "X",
                    "type" to "SELECT",
                    "entityType" to "Lead",
                ),
            ).bodyAsMap()!!["id"] as String
        val optionId =
            post(
                "/api/settings/attributes/$attrId/options",
                adminToken,
                mapOf(
                    "adminName" to "Old",
                    "sortOrder" to 1,
                ),
            ).bodyAsMap()!!["id"] as String

        val result =
            put(
                "/api/settings/attributes/$attrId/options/$optionId",
                adminToken,
                mapOf(
                    "adminName" to "New",
                    "sortOrder" to 2,
                ),
            )
        assertEquals(200, result.status())
        assertEquals("New", result.bodyAsMap()!!["adminName"])
    }

    @Test
    fun `delete option removes it from attribute`() {
        val attrId =
            post(
                "/api/settings/attributes",
                adminToken,
                mapOf(
                    "code" to "d_${UUID.randomUUID().toString().take(6)}",
                    "adminName" to "X",
                    "type" to "SELECT",
                    "entityType" to "Lead",
                ),
            ).bodyAsMap()!!["id"] as String
        val optionId =
            post(
                "/api/settings/attributes/$attrId/options",
                adminToken,
                mapOf(
                    "adminName" to "To Delete",
                    "sortOrder" to 1,
                ),
            ).bodyAsMap()!!["id"] as String

        assertEquals(204, delete("/api/settings/attributes/$attrId/options/$optionId", adminToken).status())
        val attr = get("/api/settings/attributes/$attrId", adminToken).bodyAsMap()!!
        assertTrue((attr["options"] as List<*>).isEmpty())
    }

    // ── Attribute values ──────────────────────────────────────────────────

    @Test
    fun `set and get attribute value for entity`() {
        val attrId =
            post(
                "/api/settings/attributes",
                adminToken,
                validCreateRequest("Lead"),
            ).bodyAsMap()!!["id"] as String
        val entityId = UUID.randomUUID()

        val setValue =
            post(
                "/api/settings/attributes/values",
                adminToken,
                mapOf(
                    "attributeId" to attrId,
                    "entityId" to entityId.toString(),
                    "entityType" to "Lead",
                    "value" to "Custom value",
                ),
            )
        assertEquals(200, setValue.status())
        assertEquals("Custom value", setValue.bodyAsMap()!!["value"])

        val getValues = get("/api/settings/attributes/values?entityId=$entityId&entityType=Lead", adminToken)
        assertEquals(200, getValues.status())
        assertEquals(1, getValues.bodyAsList()!!.size)
    }

    @Test
    fun `set attribute value twice updates existing`() {
        val attrId =
            post(
                "/api/settings/attributes",
                adminToken,
                validCreateRequest("Lead"),
            ).bodyAsMap()!!["id"] as String
        val entityId = UUID.randomUUID()
        val payload = mapOf("attributeId" to attrId, "entityId" to entityId.toString(), "entityType" to "Lead")

        post("/api/settings/attributes/values", adminToken, payload + mapOf("value" to "First"))
        val updated = post("/api/settings/attributes/values", adminToken, payload + mapOf("value" to "Second"))
        assertEquals("Second", updated.bodyAsMap()!!["value"])

        // Only one record in DB
        assertEquals(
            1,
            get("/api/settings/attributes/values?entityId=$entityId&entityType=Lead", adminToken).bodyAsList()!!.size,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validCreateRequest(entityType: String) =
        mapOf(
            "code" to "attr_${UUID.randomUUID().toString().take(6)}",
            "adminName" to "Custom Field",
            "type" to "TEXT",
            "entityType" to entityType,
            "sortOrder" to 0,
        )
}
