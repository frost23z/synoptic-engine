package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

class OrganizationFactory(
    private val http: TestHttp,
) {
    fun create(
        token: String,
        name: String = "Org-${UUID.randomUUID().toString().take(8)}",
        website: String? = null,
        industry: String? = null,
    ): Map<String, Any> {
        val body =
            buildMap<String, Any?> {
                put("name", name)
                if (website != null) put("website", website)
                if (industry != null) put("industry", industry)
            }
        val result = http.post("/api/contacts/organizations", token, body)
        return http.bodyAsMap(result)
            ?: error(
                "organization creation failed: status=${result.response.status} body=${result.response.contentAsString}",
            )
    }

    fun id(
        token: String,
        name: String = "Org-${UUID.randomUUID().toString().take(8)}",
    ): UUID = UUID.fromString(create(token, name)["id"] as String)
}
