package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the /api/settings/config surface that previously had zero integration tests
 * (09 P3-2). Settings drives bootstrap-time configuration, so a regression that drops
 * the update path is invisible without a test boundary here.
 */
class SystemConfigIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `admin lists system configs grouped by section`() {
        val token = adminToken()
        val result = get("/api/settings/config", token)
        assertEquals(200, result.status(), result.response.contentAsString)
        val groups = result.response.contentAsString
        // The bootstrap inserts at least 'general.*' and 'mail.*' rows.
        assertTrue(groups.contains("general"), "Expected 'general' group in response: $groups")
        assertTrue(groups.contains("mail"), "Expected 'mail' group in response: $groups")
    }

    @Test
    fun `admin reads then updates a single config by code`() {
        val token = adminToken()

        val initial = get("/api/settings/config/general.company_name", token)
        assertEquals(200, initial.status())

        val update =
            put(
                "/api/settings/config/general.company_name",
                token,
                mapOf("value" to "Acme Updated Co"),
            )
        assertEquals(200, update.status(), update.response.contentAsString)
        val body = update.bodyAsMap()!!
        assertEquals("general.company_name", body["code"])
        assertEquals("Acme Updated Co", body["value"])

        val readBack = get("/api/settings/config/general.company_name", token)
        assertEquals("Acme Updated Co", readBack.bodyAsMap()!!["value"])
    }

    @Test
    fun `secret config values are masked in the response`() {
        val token = adminToken()
        // Set a value on a known secret config (mail.password is is_secret = true).
        val update =
            put(
                "/api/settings/config/mail.password",
                token,
                mapOf("value" to "supersecret"),
            )
        assertEquals(200, update.status(), update.response.contentAsString)
        val masked = update.bodyAsMap()!!["value"] as? String
        assertNotNull(masked)
        assertFalse(masked.contains("supersecret"), "Secret value leaked in response: $masked")
        assertEquals("***", masked)
    }

    @Test
    fun `unknown code returns 404`() {
        val token = adminToken()
        val result = get("/api/settings/config/does.not.exist", token)
        assertEquals(404, result.status())
    }

    @Test
    fun `salesperson without settings_view is denied list`() {
        val token = salespersonToken()
        val result = get("/api/settings/config", token)
        assertEquals(403, result.status())
    }
}
