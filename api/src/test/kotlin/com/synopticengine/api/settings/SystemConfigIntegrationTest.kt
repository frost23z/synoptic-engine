package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the /api/settings/config surface that previously had no integration test
 * (09 P3-2). One happy path that exercises list → get → update → masking in a single
 * round-trip, plus the 403 denial path; finer-grained validation (404, blank update,
 * etc.) already lives in `SystemConfigService` unit logic — duplicating it here would
 * add bootup cost without new coverage.
 */
class SystemConfigIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `admin round-trips a config and gets the masked value back for secrets`() {
        val token = adminToken()

        val groups = get("/api/settings/config", token)
        assertEquals(200, groups.status(), groups.response.contentAsString)
        assertTrue(
            groups.response.contentAsString.contains("general") &&
                groups.response.contentAsString.contains("mail"),
            "expected at least the 'general' and 'mail' groups in the seed bundle",
        )

        val updated =
            put(
                "/api/settings/config/general.company_name",
                token,
                mapOf("value" to "Acme Updated Co"),
            )
        assertEquals(200, updated.status(), updated.response.contentAsString)
        assertEquals("Acme Updated Co", updated.bodyAsMap()!!["value"])
        assertEquals(
            "Acme Updated Co",
            get("/api/settings/config/general.company_name", token).bodyAsMap()!!["value"],
            "GET after PUT should reflect the new value",
        )

        // Secret values come back masked, even on the response to the PUT that set them —
        // that's the contract that keeps `mail.password` etc. out of admin UIs.
        val secret = put("/api/settings/config/mail.password", token, mapOf("value" to "supersecret"))
        val masked = secret.bodyAsMap()!!["value"] as String
        assertEquals("***", masked)
        assertFalse(masked.contains("supersecret"))
    }

    @Test
    fun `salesperson without settings_view is denied`() {
        assertEquals(403, get("/api/settings/config", salespersonToken()).status())
    }
}
