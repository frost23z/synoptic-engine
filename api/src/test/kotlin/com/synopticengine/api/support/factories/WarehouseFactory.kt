package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

class WarehouseFactory(
    private val http: TestHttp,
) {
    fun create(
        token: String,
        name: String = "Warehouse-${UUID.randomUUID().toString().take(8)}",
        description: String? = null,
        contactName: String? = null,
        contactEmail: String? = null,
    ): Map<String, Any> {
        val body =
            buildMap<String, Any?> {
                put("name", name)
                if (description != null) put("description", description)
                if (contactName != null) put("contactName", contactName)
                if (contactEmail != null) put("contactEmail", contactEmail)
            }
        val result = http.post("/api/warehouses", token, body)
        return http.bodyAsMap(result)
            ?: error(
                "warehouse creation failed: status=${result.response.status} body=${result.response.contentAsString}",
            )
    }

    fun id(
        token: String,
        name: String = "Warehouse-${UUID.randomUUID().toString().take(8)}",
    ): UUID = UUID.fromString(create(token, name)["id"] as String)
}
