package com.synopticengine.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TenantScopingIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `leads endpoint returns 200 with tenant filter active`() {
        val token = adminToken()
        val result = get("/api/leads", token)
        assertEquals(
            200,
            result.status(),
            "Expected 200 but got ${result.status()}: ${result.response.contentAsString}",
        )
    }

    @Test
    fun `contacts endpoint returns 200 with tenant filter active`() {
        val token = adminToken()
        val result = get("/api/contacts/persons", token)
        assertEquals(
            200,
            result.status(),
            "Expected 200 but got ${result.status()}: ${result.response.contentAsString}",
        )
    }

    @Test
    fun `unmapped route returns 404 not 500`() {
        val token = adminToken()
        val result = get("/api/this-route-does-not-exist", token)
        assertEquals(
            404,
            result.status(),
            "Unmapped paths must surface as 404, not the catch-all 500: ${result.response.contentAsString}",
        )
    }
}
