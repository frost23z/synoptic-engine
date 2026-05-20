package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

/**
 * Creates leads via the public API. `pipelineId`/`stageId` default to the
 * seeded defaults server-side (see `CreateLeadRequest`), so most tests only
 * need to pass a title.
 */
class LeadFactory(
    private val http: TestHttp,
    @Suppress("unused") private val personFactory: PersonFactory,
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
        amount: Number? = null,
    ): Map<String, Any> {
        val body =
            buildMap<String, Any?> {
                put("title", title)
                if (personId != null) put("personId", personId.toString())
                if (organizationId != null) put("organizationId", organizationId.toString())
                if (pipelineId != null) put("pipelineId", pipelineId)
                if (stageId != null) put("stageId", stageId)
                if (leadSourceId != null) put("leadSourceId", leadSourceId)
                if (leadTypeId != null) put("leadTypeId", leadTypeId)
                if (amount != null) put("amount", amount)
            }
        val result = http.post("/api/leads", token, body)
        return http.bodyAsMap(result)
            ?: error(
                "lead creation failed: status=${result.response.status} body=${result.response.contentAsString}",
            )
    }

    fun id(
        token: String,
        title: String = "Lead-${UUID.randomUUID().toString().take(8)}",
    ): UUID = UUID.fromString(create(token, title)["id"] as String)
}
