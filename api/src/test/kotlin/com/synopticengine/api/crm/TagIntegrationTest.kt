package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.TagFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TagIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tagFactory: TagFactory

    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    @Test
    fun `list tags without token returns 401`() {
        assertEquals(401, get("/api/tags", null).status())
    }

    @Test
    fun `list tags as VIEWER returns 200`() {
        assertEquals(200, get("/api/tags", viewerToken).status())
    }

    @Test
    fun `create tag returns 201 with color preserved`() {
        val result =
            post(
                "/api/tags",
                adminToken,
                mapOf("name" to "VIP-${UUID.randomUUID().toString().take(8)}", "color" to "#FF0000"),
            )
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("#FF0000", body["color"])
    }

    @Test
    fun `create tag with duplicate name returns 409`() {
        val name = "TAG-${UUID.randomUUID().toString().take(8)}"
        tagFactory.create(adminToken, name = name)
        assertEquals(409, post("/api/tags", adminToken, mapOf("name" to name)).status())
    }

    @Test
    fun `update tag returns 200`() {
        val id = tagFactory.id(adminToken)
        val newName = "NEW-${UUID.randomUUID().toString().take(8)}"
        val result = put("/api/tags/$id", adminToken, mapOf("name" to newName, "color" to "#00FF00"))
        assertEquals(200, result.status())
        assertEquals(newName, result.bodyAsMap()!!["name"])
    }

    @Test
    fun `delete tag returns 204 and is unfindable`() {
        val id = tagFactory.id(adminToken)
        assertEquals(204, delete("/api/tags/$id", adminToken).status())
        assertEquals(404, get("/api/tags/$id", adminToken).status())
    }

    @Test
    fun `search tags returns matching results`() {
        val unique = "SRCH${UUID.randomUUID().toString().take(6)}"
        tagFactory.create(adminToken, name = unique)
        val result = get("/api/tags/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        assertEquals(1, result.bodyAsList()!!.size)
    }
}
