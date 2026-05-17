package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class QuoteLeadProductsIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `GET quotes lead-products returns empty list for unknown lead`() {
        val token = adminToken()
        val result = get("/api/quotes/lead-products/${UUID.randomUUID()}", token)
        assertEquals(200, result.status(), result.response.contentAsString)
        assertEquals(0, result.bodyAsList()!!.size)
    }
}
