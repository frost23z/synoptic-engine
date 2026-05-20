package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.PersonFactory
import com.synopticengine.api.support.factories.TagFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersonIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var personFactory: PersonFactory

    @Autowired private lateinit var tagFactory: TagFactory

    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    @Test
    fun `list persons without token returns 401`() {
        assertEquals(401, get("/api/contacts/persons", null).status())
    }

    @Test
    fun `list persons as SALESPERSON returns 200`() {
        assertEquals(200, get("/api/contacts/persons", salespersonToken).status())
    }

    @Test
    fun `create person returns 201`() {
        val result = post("/api/contacts/persons", adminToken, validCreateRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertNotNull(body["fullName"])
        assertTrue((result.bodyAsMap()!!["tags"] as List<*>).isEmpty())
    }

    @Test
    fun `create person with blank first name returns 422`() {
        val request = mapOf("firstName" to "  ", "lastName" to "Smith")
        assertEquals(422, post("/api/contacts/persons", adminToken, request).status())
    }

    @Test
    fun `get person by id returns full detail`() {
        val id = post("/api/contacts/persons", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/contacts/persons/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get person by unknown id returns 404`() {
        assertEquals(404, get("/api/contacts/persons/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update person returns 200 with updated fields`() {
        val id = post("/api/contacts/persons", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result =
            put(
                "/api/contacts/persons/$id",
                adminToken,
                mapOf(
                    "firstName" to "Jane",
                    "lastName" to "Doe",
                    "jobTitle" to "CEO",
                ),
            )
        assertEquals(200, result.status())
        assertEquals("Jane Doe", result.bodyAsMap()!!["fullName"])
        assertEquals("CEO", result.bodyAsMap()!!["jobTitle"])
    }

    @Test
    fun `delete person returns 204 and is unfindable`() {
        val id = post("/api/contacts/persons", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/contacts/persons/$id", adminToken).status())
        assertEquals(404, get("/api/contacts/persons/$id", adminToken).status())
    }

    @Test
    fun `attach and detach tag on person`() {
        val personId = personFactory.id(adminToken)
        val tagId = tagFactory.id(adminToken)

        val attached = post("/api/contacts/persons/$personId/tags", adminToken, mapOf("tagId" to tagId.toString()))
        assertEquals(200, attached.status())
        @Suppress("UNCHECKED_CAST")
        val tags = attached.bodyAsMap()!!["tags"] as List<Map<String, Any>>
        assertEquals(1, tags.size)
        assertEquals(tagId.toString(), tags.first()["id"])

        val detached = delete("/api/contacts/persons/$personId/tags/$tagId", adminToken)
        assertEquals(200, detached.status())
        @Suppress("UNCHECKED_CAST")
        assertTrue((detached.bodyAsMap()!!["tags"] as List<*>).isEmpty())
    }

    @Test
    fun `search persons returns paginated results`() {
        val unique = "JANE${UUID.randomUUID().toString().take(6)}"
        personFactory.create(adminToken, firstName = unique, lastName = "Test")
        val result = get("/api/contacts/persons/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        val content = result.bodyAsMap()!!["content"] as List<*>
        assertTrue(content.isNotEmpty())
    }

    private fun validCreateRequest() =
        mapOf(
            "firstName" to "John",
            "lastName" to "Smith",
            "email" to "john.smith.${UUID.randomUUID().toString().take(6)}@test.com",
            "phone" to "+1234567890",
            "jobTitle" to "Developer",
        )
}
