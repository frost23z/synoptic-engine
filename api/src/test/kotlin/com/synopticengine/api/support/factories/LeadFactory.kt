package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

/**
 * Creates leads via the public API. By default we synthesise a person first
 * (leads need at least a person or organisation reference) — callers can pass
 * `personId`/`organizationId` explicitly to share contacts across leads.
 */
class LeadFactory(
    private val http: TestHttp,
    private val personFactory: PersonFactory,
) {
    fun create(
        token: String,
        title: String = "Lead-${UUID.randomUUID().toString().take(8)}",
        personId: UUID? = null,
        organizationId: UUID? = null,
        pipelineId: String? = null,
        stageId: String? = null,
        leadSourceId: String? = null,
        leadTypeId: String? = null,
        expectedValue: Number? = null,
    ): Map<String, Any> {
        val resolvedPersonId =
            personId
                ?: if (organizationId == null) personFactory.id(token) else null

        val body =
            buildMap<String, Any?> {
                put("title", title)
                if (resolvedPersonId != null) put("personId", resolvedPersonId.toString())
                if (organizationId != null) put("organizationId", organizationId.toString())
                if (pipelineId != null) put("pipelineId", pipelineId)
                if (stageId != null) put("stageId", stageId)
                if (leadSourceId != null) put("leadSourceId", leadSourceId)
                if (leadTypeId != null) put("leadTypeId", leadTypeId)
                if (expectedValue != null) put("expectedValue", expectedValue)
            }
        val result = http.post("/api/leads", token, body)
        return http.bodyAsMap(result)
            ?: error("lead creation failed: status=${result.response.status} body=${result.response.contentAsString}")
    }
}
