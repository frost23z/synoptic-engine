package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PipelineReorderIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `PATCH pipelines-id-stages-reorder with empty list returns 200`() {
        val token = adminToken()
        val pipelineId = get("/api/pipelines", token).bodyAsList()!!.first()["id"]
        val result = patch("/api/pipelines/$pipelineId/stages/reorder", token, mapOf("order" to emptyList<Any>()))
        assertEquals(200, result.status(), result.response.contentAsString)
    }
}
