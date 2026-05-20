package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.util.UUID

class QuoteFactory(
    private val http: TestHttp,
    private val leadFactory: LeadFactory,
) {
    fun create(
        token: String,
        leadId: UUID? = null,
        title: String = "Quote-${UUID.randomUUID().toString().take(8)}",
        items: List<Map<String, Any?>> = listOf(mapOf("quantity" to 1, "unitPrice" to 500.00, "discount" to 0)),
        discount: Number? = null,
        tax: Number? = null,
    ): Map<String, Any> {
        val resolvedLeadId = leadId ?: leadFactory.id(token)
        val body =
            buildMap<String, Any?> {
                put("leadId", resolvedLeadId.toString())
                put("title", title)
                put("items", items)
                if (discount != null) put("discount", discount)
                if (tax != null) put("tax", tax)
            }
        val result = http.post("/api/quotes", token, body)
        return http.bodyAsMap(result)
            ?: error(
                "quote creation failed: status=${result.response.status} body=${result.response.contentAsString}",
            )
    }
}
