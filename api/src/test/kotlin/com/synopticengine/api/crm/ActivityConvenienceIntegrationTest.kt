package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class ActivityConvenienceIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `GET warehouses-id-activities returns 200`() {
        val token = adminToken()
        val result = get("/api/warehouses/${UUID.randomUUID()}/activities", token)
        assertEquals(200, result.status(), result.response.contentAsString)
    }

    @Test
    fun `GET organizations-id-activities returns 200`() {
        val token = adminToken()
        val result = get("/api/contacts/organizations/${UUID.randomUUID()}/activities", token)
        assertEquals(200, result.status(), result.response.contentAsString)
    }
}
