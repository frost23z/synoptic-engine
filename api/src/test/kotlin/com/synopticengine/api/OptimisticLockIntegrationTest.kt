package com.synopticengine.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OptimisticLockIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `creating a lead returns 201`() {
        val token = adminToken()
        val result =
            post(
                "/leads",
                token,
                mapOf(
                    "title" to "Optimistic Lock Test Lead",
                    "leadValue" to 0,
                ),
            )
        assertEquals(
            201,
            result.status(),
            "Expected 201 but got ${result.status()}: ${result.response.contentAsString}",
        )
        val location = result.response.getHeader("Location")
        assertNotNull(location, "response should include a Location header")
    }
}
