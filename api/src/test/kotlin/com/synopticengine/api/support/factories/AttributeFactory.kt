package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

class AttributeFactory(
    private val http: TestHttp,
) {
    fun create(
        token: String,
        code: String = "attr_${UUID.randomUUID().toString().take(6)}",
        adminName: String = "Custom Field",
        type: String = "TEXT",
        entityType: String = "Lead",
        sortOrder: Int = 0,
    ): Map<String, Any> {
        val body =
            mapOf(
                "code" to code,
                "adminName" to adminName,
                "type" to type,
                "entityType" to entityType,
                "sortOrder" to sortOrder,
            )
        val result = http.post("/api/settings/attributes", token, body)
        return http.bodyAsMap(result)
            ?: error(
                "attribute creation failed: status=${result.response.status} body=${result.response.contentAsString}",
            )
    }

    fun id(
        token: String,
        code: String = "attr_${UUID.randomUUID().toString().take(6)}",
        type: String = "TEXT",
        entityType: String = "Lead",
    ): UUID = UUID.fromString(create(token, code = code, type = type, entityType = entityType)["id"] as String)
}
