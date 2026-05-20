package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

class PersonFactory(
    private val http: TestHttp,
) {
    fun create(
        token: String,
        firstName: String = "Person${UUID.randomUUID().toString().take(6)}",
        lastName: String = "Test",
        jobTitle: String? = null,
        organizationId: UUID? = null,
        contacts: List<Map<String, Any?>>? = null,
    ): Map<String, Any> {
        val body =
            buildMap<String, Any?> {
                put("firstName", firstName)
                put("lastName", lastName)
                if (jobTitle != null) put("jobTitle", jobTitle)
                if (organizationId != null) put("organizationId", organizationId.toString())
                if (contacts != null) put("contacts", contacts)
            }
        val result = http.post("/api/contacts/persons", token, body)
        return http.bodyAsMap(result)
            ?: error("person creation failed: status=${result.response.status} body=${result.response.contentAsString}")
    }

    fun id(
        token: String,
        firstName: String = "Person${UUID.randomUUID().toString().take(6)}",
        lastName: String = "Test",
    ): UUID = UUID.fromString(create(token, firstName, lastName)["id"] as String)
}
