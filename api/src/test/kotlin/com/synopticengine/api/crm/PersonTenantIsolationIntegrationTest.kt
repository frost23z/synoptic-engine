package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.PersonFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression test for the IDOR pattern uncovered by the failing
 * `EmailTenantIsolationIntegrationTest > … get-by-id should return 404`
 * assertion. The root cause was service-layer `requireX(id)` helpers using
 * `JpaRepository.findById(id)` — which goes through Hibernate's
 * `EntityManager.find()` fast path and bypasses the `@Filter("tenantFilter")`,
 * since that filter only rewrites query results, not primary-key loads.
 *
 * `PersonService.requirePerson` was one of the affected sites; this test
 * locks the fix in.
 */
class PersonTenantIsolationIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var personFactory: PersonFactory

    @Test
    fun `get person by id from another tenant returns 404 not 200`() {
        val a = tenantProvisioner.provision("person-iso-a")
        val b = tenantProvisioner.provision("person-iso-b")

        val aPerson = personFactory.create(a.token, firstName = "Alice", lastName = "A-tenant")
        val aPersonId = aPerson["id"] as String
        assertNotNull(aPersonId)

        // Tenant B asking for Tenant A's person by id must NOT return the row.
        // Before the fix, `requirePerson` used JpaRepository.findById which
        // bypassed the tenant filter and returned a 200 OK with A's data.
        val bGet = get("/api/contacts/persons/$aPersonId", b.token)
        assertTrue(
            bGet.status() == 404 || bGet.status() == 403,
            "Tenant B fetching Tenant A's person should return 404/403, got ${bGet.status()}",
        )

        // The owning tenant still sees its own row.
        val aGet = get("/api/contacts/persons/$aPersonId", a.token)
        assertEquals(200, aGet.status(), "Tenant A should still see its own person")
    }
}
