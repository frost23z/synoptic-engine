package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.identity.domain.ViewPermission
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.support.factories.ActivityFactory
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.OrganizationFactory
import com.synopticengine.api.support.factories.PersonFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 4 P0-3: list/search/filter and CSV-export endpoints must respect the
 * authenticated user's view scope. A salesperson with INDIVIDUAL view should
 * not be able to see records owned by other users via:
 *  - GET /api/contacts/persons/search
 *  - GET /api/contacts/organizations/search
 *  - GET /api/activities (filter)
 *  - GET /api/persons/export
 *  - GET /api/organizations/export
 *  - GET /api/leads/export
 *
 * Pre-fix, these endpoints bypassed [com.synopticengine.api.crm.scoping.ScopeResolver]
 * entirely (the matching `findAll` methods were scoped but `search`, `filter`, and
 * the three exporters were not). This test asserts the leak is shut.
 */
class ScopeLeakIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var personFactory: PersonFactory

    @Autowired private lateinit var organizationFactory: OrganizationFactory

    @Autowired private lateinit var activityFactory: ActivityFactory

    @Autowired private lateinit var leadFactory: LeadFactory

    @Test
    fun `person search does not leak records owned by other users`() {
        val unique = uniqueTag()
        val admin = adminToken()
        // Admin creates a person (it gets created_by = admin).
        val person = personFactory.create(admin, firstName = "Scope$unique", lastName = "Smith")
        val individualToken = individualSalespersonToken()

        val result = get("/api/contacts/persons/search?q=Scope$unique", individualToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val content = result.bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertFalse(
            content.any { it["id"] == person["id"] },
            "INDIVIDUAL salesperson saw a person they don't own: $person",
        )
    }

    @Test
    fun `organization search does not leak records owned by other users`() {
        val unique = uniqueTag()
        val admin = adminToken()
        val org = organizationFactory.create(admin, name = "ScopeOrg$unique")
        val individualToken = individualSalespersonToken()

        val result = get("/api/contacts/organizations/search?q=ScopeOrg$unique", individualToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val content = result.bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertFalse(
            content.any { it["id"] == org["id"] },
            "INDIVIDUAL salesperson saw an organization they don't own: $org",
        )
    }

    @Test
    fun `activity filter does not leak activities owned by other users`() {
        val admin = adminToken()
        val activity = activityFactory.create(admin, title = "Scope leak probe ${UUID.randomUUID()}", type = "NOTE")
        val individualToken = individualSalespersonToken()

        val result = get("/api/activities?page=0&size=200", individualToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val content = result.bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertFalse(
            content.any { it["id"] == activity["id"] },
            "INDIVIDUAL salesperson saw an activity they don't own: $activity",
        )
    }

    @Test
    fun `persons export does not leak records owned by other users`() {
        val unique = uniqueTag()
        val admin = adminToken()
        personFactory.create(admin, firstName = "Export$unique", lastName = "Smith")
        val individualToken = individualSalespersonToken()

        val result = get("/api/persons/export", individualToken)
        assertEquals(200, result.status())
        val csv = result.response.contentAsString
        assertFalse(
            csv.contains("Export$unique"),
            "INDIVIDUAL salesperson's CSV export included a person they don't own:\n$csv",
        )
    }

    @Test
    fun `organizations export does not leak records owned by other users`() {
        val unique = uniqueTag()
        val admin = adminToken()
        organizationFactory.create(admin, name = "ExportOrg$unique")
        val individualToken = individualSalespersonToken()

        val result = get("/api/organizations/export", individualToken)
        assertEquals(200, result.status())
        val csv = result.response.contentAsString
        assertFalse(
            csv.contains("ExportOrg$unique"),
            "INDIVIDUAL salesperson's CSV export included an organization they don't own:\n$csv",
        )
    }

    @Test
    fun `leads export does not leak records owned by other users`() {
        val admin = adminToken()
        val uniqueTitle = "ExportLead${UUID.randomUUID()}"
        leadFactory.create(admin, title = uniqueTitle)
        val individualToken = individualSalespersonToken()

        val result = get("/api/leads/export", individualToken)
        assertEquals(200, result.status())
        val csv = result.response.contentAsString
        assertFalse(
            csv.contains(uniqueTitle),
            "INDIVIDUAL salesperson's CSV export included a lead they don't own:\n$csv",
        )
    }

    @Test
    fun `INDIVIDUAL user still sees their own records via search`() {
        val unique = uniqueTag()
        val salespersonEmail = "indiv-self-${UUID.randomUUID()}@test.com"
        val salespersonToken = createIndividualSalesperson(salespersonEmail)
        personFactory.create(salespersonToken, firstName = "Self$unique", lastName = "Owned")

        val result = get("/api/contacts/persons/search?q=Self$unique", salespersonToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val content = result.bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(
            content.any { (it["firstName"] as String).startsWith("Self$unique") },
            "Salesperson should see their own record but didn't: $content",
        )
    }

    private fun uniqueTag() =
        UUID
            .randomUUID()
            .toString()
            .take(8)
            .uppercase()

    private fun individualSalespersonToken(): String =
        createIndividualSalesperson("indiv-${UUID.randomUUID()}@test.com")

    private fun createIndividualSalesperson(email: String): String {
        TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
            userService.create(
                email = email,
                password = "password123",
                firstName = "Indiv",
                lastName = "Sales",
                roleNames = setOf("SALESPERSON"),
                viewPermission = ViewPermission.INDIVIDUAL,
            )
        }
        return login(email, "password123")
    }
}
