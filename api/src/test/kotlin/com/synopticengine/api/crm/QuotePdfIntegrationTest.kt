package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class QuotePdfIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `GET quotes-id-print returns 404 for unknown quote`() {
        val token = adminToken()
        val result = get("/api/quotes/${UUID.randomUUID()}/print", token)
        assertEquals(404, result.status())
    }
}
