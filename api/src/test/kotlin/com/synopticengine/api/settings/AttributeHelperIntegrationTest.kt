package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttributeHelperIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `GET attributes-lookup returns empty list for unknown lookup`() {
        val token = adminToken()
        val result = get("/api/settings/attributes/lookup/nonexistent-lookup", token)
        assertEquals(200, result.status(), result.response.contentAsString)
        assertEquals(0, result.bodyAsList()!!.size)
    }

    @Test
    fun `GET attributes-check-unique-validation returns isUnique true for unused value`() {
        val token = adminToken()
        val result =
            get(
                "/api/settings/attributes/check-unique-validation?code=test_attr&entityType=leads&value=uniqueValue999",
                token,
            )
        assertEquals(200, result.status(), result.response.contentAsString)
        val body = result.bodyAsMap()!!
        assertNotNull(body["isUnique"])
    }

    @Test
    fun `POST attributes-mass-destroy with empty list returns 204`() {
        val token = adminToken()
        val result =
            post(
                "/api/settings/attributes/mass-destroy",
                token,
                mapOf("ids" to emptyList<String>()),
            )
        assertEquals(204, result.status(), result.response.contentAsString)
    }

    @Test
    fun `POST attributes-mass-update with empty list returns 204`() {
        val token = adminToken()
        val result =
            post(
                "/api/settings/attributes/mass-update",
                token,
                mapOf("ids" to emptyList<String>()),
            )
        assertEquals(204, result.status(), result.response.contentAsString)
    }

    @Test
    fun `GET attributes-download returns 204`() {
        val token = adminToken()
        val result = get("/api/settings/attributes/download", token)
        assertEquals(204, result.status())
    }

    @Test
    fun `GET attributes-download export true returns csv`() {
        val token = adminToken()
        val result = get("/api/settings/attributes/download?export=true", token)
        assertEquals(200, result.status())
        assertContains(result.response.contentType ?: "", "text/csv")
        assertContains(result.response.contentAsString, "id,code,admin_name,type,entity_type,sort_order,is_user_defined")
    }
}
