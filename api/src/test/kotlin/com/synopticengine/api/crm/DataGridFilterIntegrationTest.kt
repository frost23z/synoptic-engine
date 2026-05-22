package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DataGridFilterIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `full datagrid filter lifecycle`() {
        val token = adminToken()

        val createResult =
            post(
                "/api/datagrid/saved-filters",
                token,
                mapOf("name" to "My Open Leads", "src" to "leads", "applied" to mapOf("status" to "open")),
            )
        assertEquals(201, createResult.status(), createResult.response.contentAsString)
        val filterId = createResult.bodyAsMap()!!["id"]
        assertNotNull(filterId)

        val listResult = get("/api/datagrid/saved-filters?src=leads", token)
        assertEquals(200, listResult.status())
        assertEquals(1, listResult.bodyAsList()!!.size)

        val updateResult =
            put(
                "/api/datagrid/saved-filters/$filterId",
                token,
                mapOf("name" to "Updated", "applied" to mapOf("status" to "open")),
            )
        assertEquals(200, updateResult.status())

        val deleteResult = delete("/api/datagrid/saved-filters/$filterId", token)
        assertEquals(204, deleteResult.status())

        val afterDelete = get("/api/datagrid/saved-filters?src=leads", token)
        assertEquals(0, afterDelete.bodyAsList()!!.size)
    }

    @Test
    fun `save rejects unsupported source and nested applied payload`() {
        val token = adminToken()
        assertEquals(
            400,
            post(
                "/api/datagrid/saved-filters",
                token,
                mapOf("name" to "bad", "src" to "unknown", "applied" to mapOf("status" to "open")),
            ).status(),
        )
        assertEquals(
            400,
            post(
                "/api/datagrid/saved-filters",
                token,
                mapOf(
                    "name" to "bad",
                    "src" to "leads",
                    "applied" to mapOf("status" to mapOf("op" to "eq", "value" to "open")),
                ),
            ).status(),
        )
    }
}
