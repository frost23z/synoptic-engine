package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

/**
 * Resolves the seeded default pipeline + first stage so tests that just need
 * "any valid pipeline/stage" don't have to fetch and parse the list themselves.
 * No `create()` — pipelines are seed data, not test fixtures.
 */
class PipelineResolver(
    private val http: TestHttp,
) {
    data class DefaultPipelineStage(
        val pipelineId: String,
        val stageId: String,
    )

    fun defaultPipelineAndStage(token: String): DefaultPipelineStage {
        val pipelines =
            http.bodyAsList(http.get("/api/pipelines", token))
                ?: error("pipeline list returned empty")
        val first = pipelines.first()
        val pipelineId = first["id"] as String

        @Suppress("UNCHECKED_CAST")
        val stages = first["stages"] as List<Map<String, Any>>
        val stageId = stages.first()["id"] as String
        return DefaultPipelineStage(pipelineId, stageId)
    }
}
