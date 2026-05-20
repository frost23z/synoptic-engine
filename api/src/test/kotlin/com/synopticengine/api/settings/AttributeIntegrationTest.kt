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

class AttributeIntegrationTest : AbstractIntegrationTest() {
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
    fun `list attributes without token returns 401`() {
        assertEquals(401, get("/api/settings/attributes", null).status())
    }

    @Test
    fun `list attributes as SALESPERSON returns 403`() {
        assertEquals(403, get("/api/settings/attributes", salespersonToken).status())
    }

    @Test
    fun `create attribute as SALESPERSON returns 403`() {
        assertEquals(
            403,
            post(
                "/api/settings/attributes",
                salespersonToken,
                mapOf("code" to "x", "adminName" to "x", "type" to "TEXT", "entityType" to "Lead", "sortOrder" to 0),
            ).status(),
        )
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create attribute returns 201 with empty options`() {
        val body = attributeFactory.create(adminToken)
        assertNotNull(body["id"])
        assertNotNull(body["code"])
        assertEquals("Lead", body["entityType"])
        assertEquals("TEXT", body["type"])
        assertTrue((body["options"] as List<*>).isEmpty())
    }

    @Test
    fun `create duplicate code for same entityType returns 409`() {
        val code = "dup_${UUID.randomUUID().toString().take(6)}"
        attributeFactory.create(adminToken, code = code, entityType = "Lead")
        assertEquals(
            409,
            post(
                "/api/settings/attributes",
                adminToken,
                mapOf("code" to code, "adminName" to "X", "type" to "TEXT", "entityType" to "Lead", "sortOrder" to 0),
            ).status(),
        )
    }

    @Test
    fun `same code for different entityType is allowed`() {
        val code = "code_${UUID.randomUUID().toString().take(6)}"
        attributeFactory.create(adminToken, code = code, entityType = "Lead")
        attributeFactory.create(adminToken, code = code, entityType = "Person")
        // No assertion needed — factory throws if either creation fails.
    }

    @Test
    fun `get attribute by id returns detail`() {
        val id = attributeFactory.id(adminToken)
        val result = get("/api/settings/attributes/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id.toString(), result.bodyAsMap()!!["id"])
    }

    @Test
    fun `filter attributes by entityType returns only matching`() {
        attributeFactory.create(adminToken, entityType = "Organization")
        val body = get("/api/settings/attributes?entityType=Organization", adminToken).bodyAsList()!!
        assertTrue(body.all { it["entityType"] == "Organization" })
    }

    @Test
    fun `update attribute returns 200 with updated fields`() {
        val id = attributeFactory.id(adminToken)
        val result =
            put(
                "/api/settings/attributes/$id",
                adminToken,
                mapOf("adminName" to "Updated Name", "type" to "TEXTAREA", "sortOrder" to 5),
            )
        assertEquals(200, result.status())
        assertEquals("Updated Name", result.bodyAsMap()!!["adminName"])
        assertEquals("TEXTAREA", result.bodyAsMap()!!["type"])
    }

    @Test
    fun `delete attribute returns 204 and is unfindable`() {
        val id = attributeFactory.id(adminToken)
        assertEquals(204, delete("/api/settings/attributes/$id", adminToken).status())
        assertEquals(404, get("/api/settings/attributes/$id", adminToken).status())
    }

    // ── Options on SELECT attribute ───────────────────────────────────────

    @Test
    fun `add update and delete option on SELECT attribute`() {
        val attrId = attributeFactory.id(adminToken, type = "SELECT")
        val optionId =
            post(
                "/api/settings/attributes/$attrId/options",
                adminToken,
                mapOf("adminName" to "Hot", "sortOrder" to 1),
            ).bodyAsMap()!!["id"] as String
        assertEquals(1, (get("/api/settings/attributes/$attrId", adminToken).bodyAsMap()!!["options"] as List<*>).size)

        val updated =
            put(
                "/api/settings/attributes/$attrId/options/$optionId",
                adminToken,
                mapOf("adminName" to "Warm", "sortOrder" to 2),
            )
        assertEquals(200, updated.status())
        assertEquals("Warm", updated.bodyAsMap()!!["adminName"])

        assertEquals(204, delete("/api/settings/attributes/$attrId/options/$optionId", adminToken).status())
        assertTrue(
            (get("/api/settings/attributes/$attrId", adminToken).bodyAsMap()!!["options"] as List<*>).isEmpty(),
        )
    }

    // ── Attribute values ──────────────────────────────────────────────────

    @Test
    fun `set attribute value upserts (second write replaces first)`() {
        val attrId = attributeFactory.id(adminToken)
        val entityId = UUID.randomUUID()
        val payload =
            mapOf(
                "attributeId" to attrId.toString(),
                "entityId" to entityId.toString(),
                "entityType" to "Lead",
            )

        val first = post("/api/settings/attributes/values", adminToken, payload + ("value" to "First"))
        assertEquals(200, first.status())
        assertEquals("First", first.bodyAsMap()!!["value"])

        val second = post("/api/settings/attributes/values", adminToken, payload + ("value" to "Second"))
        assertEquals("Second", second.bodyAsMap()!!["value"])

        val list = get("/api/settings/attributes/values?entityId=$entityId&entityType=Lead", adminToken).bodyAsList()!!
        assertEquals(1, list.size, "second post must upsert, not duplicate")
    }
}
