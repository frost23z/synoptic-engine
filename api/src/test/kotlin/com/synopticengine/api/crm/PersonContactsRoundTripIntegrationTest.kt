package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P1.4 acceptance: a Person with multiple emails and contact numbers round-trips
 * through the JSONB columns introduced in V031 without losing structure, and the
 * legacy `email` / `phone` fields stay populated for back-compat readers.
 */
class PersonContactsRoundTripIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `multiple emails and contact numbers round-trip through the API`() {
        val token = adminToken()
        val unique = UUID.randomUUID().toString().take(6)

        val createBody =
            mapOf(
                "firstName" to "Multi",
                "lastName" to "Contact",
                "emails" to
                    listOf(
                        mapOf("value" to "primary-$unique@test.com", "label" to "primary"),
                        mapOf("value" to "work-$unique@test.com", "label" to "work"),
                    ),
                "contactNumbers" to
                    listOf(
                        mapOf("value" to "+1-555-0100", "label" to "primary"),
                        mapOf("value" to "+1-555-0101", "label" to "mobile"),
                    ),
            )

        val createResult = post("/api/contacts/persons", token, createBody)
        assertEquals(
            201,
            createResult.status(),
            "Expected 201 but got ${createResult.status()}: ${createResult.response.contentAsString}",
        )
        val created = createResult.bodyAsMap()!!
        val id = created["id"] as String

        @Suppress("UNCHECKED_CAST")
        val createdEmails = created["emails"] as List<Map<String, Any>>
        assertEquals(2, createdEmails.size, "Both supplied emails should be persisted")
        assertEquals("primary-$unique@test.com", createdEmails[0]["value"])
        assertEquals("primary", createdEmails[0]["label"])
        assertEquals("work-$unique@test.com", createdEmails[1]["value"])
        assertEquals("work", createdEmails[1]["label"])

        @Suppress("UNCHECKED_CAST")
        val createdNumbers = created["contactNumbers"] as List<Map<String, Any>>
        assertEquals(2, createdNumbers.size, "Both supplied contact numbers should be persisted")

        // Legacy scalars stay populated from the first entry, so existing readers keep working.
        assertEquals("primary-$unique@test.com", created["email"])
        assertEquals("+1-555-0100", created["phone"])

        // GET round-trip preserves the arrays.
        val getResult = get("/api/contacts/persons/$id", token)
        assertEquals(200, getResult.status())
        @Suppress("UNCHECKED_CAST")
        val fetchedEmails = getResult.bodyAsMap()!!["emails"] as List<Map<String, Any>>
        assertEquals(2, fetchedEmails.size)
        assertTrue(fetchedEmails.any { it["label"] == "work" })

        // Update with a different set: the persisted array is replaced, not appended.
        val updateBody =
            mapOf(
                "firstName" to "Multi",
                "lastName" to "Contact",
                "emails" to
                    listOf(
                        mapOf("value" to "updated-$unique@test.com", "label" to "primary"),
                    ),
                "contactNumbers" to emptyList<Map<String, Any>>(),
            )
        val updateResult = put("/api/contacts/persons/$id", token, updateBody)
        assertEquals(200, updateResult.status())
        @Suppress("UNCHECKED_CAST")
        val updatedEmails = updateResult.bodyAsMap()!!["emails"] as List<Map<String, Any>>
        assertEquals(1, updatedEmails.size, "Update replaces the emails array")
        assertEquals("updated-$unique@test.com", updatedEmails[0]["value"])
        @Suppress("UNCHECKED_CAST")
        val updatedNumbers = updateResult.bodyAsMap()!!["contactNumbers"] as List<Map<String, Any>>
        assertTrue(updatedNumbers.isEmpty(), "Update with empty contactNumbers clears the array")
    }

    @Test
    fun `legacy email-only payload still works and seeds the JSONB array`() {
        val token = adminToken()
        val unique = UUID.randomUUID().toString().take(6)

        val createResult =
            post(
                "/api/contacts/persons",
                token,
                mapOf(
                    "firstName" to "Legacy",
                    "lastName" to "Caller",
                    "email" to "legacy-$unique@test.com",
                    "phone" to "+1-555-9999",
                ),
            )
        assertEquals(201, createResult.status())
        val body = createResult.bodyAsMap()!!

        // Both legacy fields and the JSONB arrays are populated from the scalar input.
        assertEquals("legacy-$unique@test.com", body["email"])
        @Suppress("UNCHECKED_CAST")
        val emails = body["emails"] as List<Map<String, Any>>
        assertEquals(1, emails.size)
        assertEquals("legacy-$unique@test.com", emails[0]["value"])
        assertEquals("primary", emails[0]["label"])

        @Suppress("UNCHECKED_CAST")
        val numbers = body["contactNumbers"] as List<Map<String, Any>>
        assertEquals(1, numbers.size)
        assertEquals("+1-555-9999", numbers[0]["value"])
    }
}
