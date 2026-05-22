package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 3 / P3.5 — public web form submission actually creates a person, and
 * optionally a lead.
 */
class WebFormSubmissionIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    @Test
    fun `public submit creates a person from form values`() {
        val firstNameAttr = createAttribute("first_name")
        val emailAttr = createAttribute("email")
        val formId =
            post(
                "/api/settings/web-forms",
                adminToken,
                mapOf(
                    "title" to "Contact form ${UUID.randomUUID()}",
                    "isActive" to true,
                    "fields" to
                        listOf(
                            mapOf("attributeId" to firstNameAttr, "sortOrder" to 1, "isRequired" to true),
                            mapOf("attributeId" to emailAttr, "sortOrder" to 2, "isRequired" to true),
                        ),
                ),
            ).bodyAsMap()!!["id"] as String

        val email = "wf-${UUID.randomUUID().toString().take(8)}@example.com"
        val result =
            post(
                "/web-forms/$formId/submit",
                null,
                mapOf(
                    "values" to
                        mapOf(
                            "first_name" to "Jane",
                            "email" to email,
                        ),
                ),
            )
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(true, body["success"])
        val personId = body["personId"] as String?
        assertNotNull(personId)

        // Person is in the same tenant as the form — admin can find it.
        val personResp = get("/api/contacts/persons/$personId", adminToken)
        assertEquals(200, personResp.status())
        assertEquals("Jane", personResp.bodyAsMap()!!["firstName"])
        assertEquals(email, personResp.bodyAsMap()!!["email"])
    }

    @Test
    fun `public submit also creates a lead when lead_title is supplied`() {
        val firstNameAttr = createAttribute("first_name")
        val emailAttr = createAttribute("email")
        val leadTitleAttr = createAttribute("lead_title")
        val formId =
            post(
                "/api/settings/web-forms",
                adminToken,
                mapOf(
                    "title" to "Lead form ${UUID.randomUUID()}",
                    "isActive" to true,
                    "createLead" to true,
                    "fields" to
                        listOf(
                            mapOf("attributeId" to firstNameAttr, "sortOrder" to 1, "isRequired" to true),
                            mapOf("attributeId" to emailAttr, "sortOrder" to 2, "isRequired" to false),
                            mapOf("attributeId" to leadTitleAttr, "sortOrder" to 3, "isRequired" to false),
                        ),
                ),
            ).bodyAsMap()!!["id"] as String

        val result =
            post(
                "/web-forms/$formId/submit",
                null,
                mapOf(
                    "values" to
                        mapOf(
                            "first_name" to "Buyer",
                            "email" to "buyer-${UUID.randomUUID().toString().take(8)}@x.test",
                            "lead_title" to "Interested in product",
                        ),
                ),
            )
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["leadId"])
        val leadId = body["leadId"] as String
        val leadResp = get("/api/leads/$leadId", adminToken)
        assertEquals(200, leadResp.status())
        assertEquals("Interested in product", leadResp.bodyAsMap()!!["title"])
    }

    @Test
    fun `submit on inactive form returns 404`() {
        val formId =
            post(
                "/api/settings/web-forms",
                adminToken,
                mapOf("title" to "Inactive", "isActive" to false),
            ).bodyAsMap()!!["id"] as String
        val resp = post("/web-forms/$formId/submit", null, mapOf("values" to mapOf("email" to "x@y.test")))
        // PublicWebFormController.submit goes via WebFormSubmissionService which
        // re-checks `isActive`. The inactive form raises NoSuchElementException.
        assertTrue(resp.status() == 404 || resp.status() == 400)
    }

    @Test
    fun `public submit does not create lead when createLead is false`() {
        val firstNameAttr = createAttribute("first_name")
        val leadTitleAttr = createAttribute("lead_title")
        val formId =
            post(
                "/api/settings/web-forms",
                adminToken,
                mapOf(
                    "title" to "Person-only form ${UUID.randomUUID()}",
                    "isActive" to true,
                    "createLead" to false,
                    "fields" to
                        listOf(
                            mapOf("attributeId" to firstNameAttr, "sortOrder" to 1, "isRequired" to true),
                            mapOf("attributeId" to leadTitleAttr, "sortOrder" to 2, "isRequired" to false),
                        ),
                ),
            ).bodyAsMap()!!["id"] as String

        val result =
            post(
                "/web-forms/$formId/submit",
                null,
                mapOf(
                    "values" to
                        mapOf(
                            "first_name" to "Only Person",
                            "lead_title" to "Should not create lead",
                        ),
                ),
            )
        assertEquals(200, result.status())
        assertEquals(null, result.bodyAsMap()!!["leadId"])
    }

    @Test
    fun `submit without any name or email is rejected`() {
        val formId =
            post(
                "/api/settings/web-forms",
                adminToken,
                mapOf("title" to "Form", "isActive" to true),
            ).bodyAsMap()!!["id"] as String
        val resp = post("/web-forms/$formId/submit", null, mapOf("values" to emptyMap<String, String>()))
        // IllegalArgumentException → 400 via GlobalExceptionHandler.
        assertEquals(400, resp.status())
    }

    /**
     * Create-or-find an attribute with the given code. The unique constraint on
     * (tenant, code, entityType) means a second call from the same suite (or
     * from a prior test that ran in this class) would 4xx — so we look up an
     * existing one if create fails.
     */
    private fun createAttribute(code: String): String {
        val resp =
            post(
                "/api/settings/attributes",
                adminToken,
                mapOf(
                    "code" to code,
                    "adminName" to code.replace('_', ' '),
                    "type" to "TEXT",
                    "entityType" to "Person",
                ),
            )
        if (resp.status() == 201) {
            return resp.bodyAsMap()!!["id"] as String
        }
        // 409 or 422 — look up an existing attribute by code.
        @Suppress("UNCHECKED_CAST")
        val all = get("/api/settings/attributes?entityType=Person", adminToken).bodyAsList()!!
        val existing = all.first { it["code"] == code }
        return existing["id"] as String
    }
}
