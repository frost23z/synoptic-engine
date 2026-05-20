package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

class TagFactory(
    private val http: TestHttp,
) {
    fun create(
        token: String,
        name: String = "tag-${UUID.randomUUID().toString().take(8)}",
    ): Map<String, Any> {
        val result = http.post("/api/tags", token, mapOf("name" to name))
        return http.bodyAsMap(result)
            ?: error("tag creation failed: status=${result.response.status} body=${result.response.contentAsString}")
    }

    fun id(
        token: String,
        name: String = "tag-${UUID.randomUUID().toString().take(8)}",
    ): UUID = UUID.fromString(create(token, name)["id"] as String)
}
