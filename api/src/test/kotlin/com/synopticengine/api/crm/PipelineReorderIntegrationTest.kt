package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelineReorderIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `GET pipelines returns default pipeline`() {
        val token = adminToken()
        val result = get("/api/pipelines", token)
        assertEquals(200, result.status())
        assertTrue(result.bodyAsList()!!.isNotEmpty())
    }

    @Test
    fun `PATCH pipelines-id-stages-reorder with empty list returns 200`() {
        val token = adminToken()
        val pipelines = get("/api/pipelines", token).bodyAsList()!!
        if (pipelines.isEmpty()) return
        val pipelineId = pipelines.first()["id"]
        val result = patch("/api/pipelines/$pipelineId/stages/reorder", token, mapOf("order" to emptyList<Any>()))
        assertEquals(200, result.status(), result.response.contentAsString)
    }
}
