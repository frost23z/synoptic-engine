package com.synopticengine.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TenantScopingIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `leads endpoint returns 200 with tenant filter active`() {
        val token = adminToken()
        val result = get("/leads", token)
        assertEquals(
            200,
            result.status(),
            "Expected 200 but got ${result.status()}: ${result.response.contentAsString}",
        )
    }

    @Test
    fun `contacts endpoint returns 200 with tenant filter active`() {
        val token = adminToken()
        val result = get("/persons", token)
        assertEquals(
            200,
            result.status(),
            "Expected 200 but got ${result.status()}: ${result.response.contentAsString}",
        )
    }
}
