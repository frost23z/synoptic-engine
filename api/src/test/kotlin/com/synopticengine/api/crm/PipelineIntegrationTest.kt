package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PipelineIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    @Test
    fun `list pipelines without token returns 401`() {
        assertEquals(401, get("/api/pipelines", null).status())
    }

    @Test
    fun `list pipelines as SALESPERSON returns 200 with seeded default`() {
        val result = get("/api/pipelines", salespersonToken)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertTrue(body.isNotEmpty())
        assertTrue(body.any { it["isDefault"] == true })
    }

    @Test
    fun `seeded default pipeline has 6 stages`() {
        val pipelines = get("/api/pipelines", adminToken).bodyAsList()!!
        val defaultPipeline = pipelines.first { it["isDefault"] == true }

        @Suppress("UNCHECKED_CAST")
        val stages = defaultPipeline["stages"] as List<Map<String, Any>>
        assertEquals(6, stages.size)
        val codes = stages.mapNotNull { it["code"] as? String }
        assertTrue(codes.contains("won"))
        assertTrue(codes.contains("lost"))
    }

    @Test
    fun `create pipeline returns 201`() {
        val name = "Test Pipeline ${UUID.randomUUID().toString().take(8)}"
        val result = post("/api/pipelines", adminToken, mapOf("name" to name, "rottenDays" to 14))
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(name, body["name"])
        assertEquals(false, body["isDefault"])
    }

    @Test
    fun `get pipeline by id includes stages`() {
        val pipelines = get("/api/pipelines", adminToken).bodyAsList()!!
        val id = pipelines.first()["id"] as String
        val result = get("/api/pipelines/$id", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsMap()!!["stages"])
    }

    @Test
    fun `get pipeline by unknown id returns 404`() {
        assertEquals(404, get("/api/pipelines/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update pipeline returns 200`() {
        val name = "UPD Pipeline ${UUID.randomUUID().toString().take(8)}"
        val id = post("/api/pipelines", adminToken, mapOf("name" to name)).bodyAsMap()!!["id"] as String
        val result =
            put(
                "/api/pipelines/$id",
                adminToken,
                mapOf("name" to "Updated Pipeline", "isActive" to true, "isDefault" to false, "rottenDays" to 60),
            )
        assertEquals(200, result.status())
        assertEquals("Updated Pipeline", result.bodyAsMap()!!["name"])
    }

    @Test
    fun `delete non-default pipeline returns 204`() {
        val id =
            post(
                "/api/pipelines",
                adminToken,
                mapOf("name" to "DEL-${UUID.randomUUID().toString().take(8)}"),
            ).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/pipelines/$id", adminToken).status())
        assertEquals(404, get("/api/pipelines/$id", adminToken).status())
    }

    @Test
    fun `delete default pipeline returns 409`() {
        val pipelines = get("/api/pipelines", adminToken).bodyAsList()!!
        val defaultId = (pipelines.first { it["isDefault"] == true })["id"] as String
        assertEquals(409, delete("/api/pipelines/$defaultId", adminToken).status())
    }

    @Test
    fun `add stage to pipeline returns 201`() {
        val pipelineId =
            post(
                "/api/pipelines",
                adminToken,
                mapOf("name" to "STGD-${UUID.randomUUID().toString().take(8)}"),
            ).bodyAsMap()!!["id"] as String

        val result =
            post(
                "/api/pipelines/$pipelineId/stages",
                adminToken,
                mapOf("name" to "Prospecting", "sortOrder" to 1, "probability" to 20),
            )
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(pipelineId, body["pipelineId"])
    }

    @Test
    fun `update stage returns 200`() {
        val pipelineId =
            post(
                "/api/pipelines",
                adminToken,
                mapOf("name" to "UPS-${UUID.randomUUID().toString().take(8)}"),
            ).bodyAsMap()!!["id"] as String
        val stageId =
            post(
                "/api/pipelines/$pipelineId/stages",
                adminToken,
                mapOf("name" to "Stage A", "sortOrder" to 1, "probability" to 10),
            ).bodyAsMap()!!["id"] as String

        val result =
            put(
                "/api/pipelines/$pipelineId/stages/$stageId",
                adminToken,
                mapOf("name" to "Stage B", "sortOrder" to 2, "probability" to 50),
            )
        assertEquals(200, result.status())
        assertEquals("Stage B", result.bodyAsMap()!!["name"])
    }

    @Test
    fun `delete stage returns 204`() {
        val pipelineId =
            post(
                "/api/pipelines",
                adminToken,
                mapOf("name" to "DS-${UUID.randomUUID().toString().take(8)}"),
            ).bodyAsMap()!!["id"] as String
        // Need a sibling stage — the service refuses to delete the only stage in a pipeline
        // since any leads on it would have nowhere to move.
        post(
            "/api/pipelines/$pipelineId/stages",
            adminToken,
            mapOf("name" to "Keeper", "sortOrder" to 1, "probability" to 50),
        )
        val stageId =
            post(
                "/api/pipelines/$pipelineId/stages",
                adminToken,
                mapOf("name" to "To Delete", "sortOrder" to 2, "probability" to 0),
            ).bodyAsMap()!!["id"] as String

        assertEquals(204, delete("/api/pipelines/$pipelineId/stages/$stageId", adminToken).status())
    }
}
