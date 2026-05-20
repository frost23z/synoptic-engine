package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.math.BigDecimal
import java.util.UUID

class ProductFactory(
    private val http: TestHttp,
) {
    fun create(
        token: String,
        name: String = "Product-${UUID.randomUUID().toString().take(8)}",
        sku: String? = null,
        price: BigDecimal = BigDecimal("100.00"),
        description: String? = null,
        isActive: Boolean = true,
    ): Map<String, Any> {
        val body =
            buildMap<String, Any?> {
                put("name", name)
                put("price", price)
                put("isActive", isActive)
                if (sku != null) put("sku", sku)
                if (description != null) put("description", description)
            }
        val result = http.post("/api/products", token, body)
        return http.bodyAsMap(result)
            ?: error(
                "product creation failed: status=${result.response.status} body=${result.response.contentAsString}",
            )
    }

    fun id(
        token: String,
        name: String = "Product-${UUID.randomUUID().toString().take(8)}",
        price: BigDecimal = BigDecimal("100.00"),
    ): UUID = UUID.fromString(create(token, name = name, price = price)["id"] as String)
}
