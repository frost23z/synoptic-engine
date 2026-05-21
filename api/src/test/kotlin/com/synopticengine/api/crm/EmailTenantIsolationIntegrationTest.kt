package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression test for the cross-tenant leak that previously affected
 * `GET /mail/{folder}`. The list endpoint runs a native SQL query against
 * `emails`, which is not (yet) covered by Postgres RLS in V007 — and was
 * also missing an explicit `tenant_id` predicate, so every tenant saw every
 * other tenant's emails.
 *
 * V011 enables RLS on the `emails` table and the previous PR added the
 * `tenant_id = :tenantId` parameter to `EmailRepository.findByFolder`. This
 * test asserts both isolation layers behave correctly end-to-end.
 */
class EmailTenantIsolationIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Test
    fun `mail folder listing only returns emails owned by the caller's tenant`() {
        val a = tenantProvisioner.provision("mail-iso-a")
        val b = tenantProvisioner.provision("mail-iso-b")

        // Tenant A drafts an email.
        val aCompose =
            post(
                "/api/mail",
                a.token,
                mapOf(
                    "to" to "alice@example.com",
                    "subject" to "Tenant A's secret",
                    "body" to "for-A-eyes-only",
                    "isDraft" to false,
                ),
            )
        assertEquals(201, aCompose.status())
        val aEmailId = aCompose.bodyAsMap()!!["id"] as String
        assertNotNull(aEmailId)

        // Tenant B drafts an email of its own (gives us something to verify the
        // happy path still works and B's list isn't empty for unrelated reasons).
        val bCompose =
            post(
                "/api/mail",
                b.token,
                mapOf(
                    "to" to "bob@example.com",
                    "subject" to "Tenant B's secret",
                    "body" to "for-B-eyes-only",
                    "isDraft" to false,
                ),
            )
        assertEquals(201, bCompose.status())
        val bEmailId = bCompose.bodyAsMap()!!["id"] as String

        // A's sent folder must contain A's email and never B's.
        @Suppress("UNCHECKED_CAST")
        val aSent = get("/api/mail?folder=sent", a.token).bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(aSent.any { it["id"] == aEmailId }, "Tenant A should see its own sent email")
        assertTrue(aSent.none { it["id"] == bEmailId }, "Tenant A must not see Tenant B's email in /mail?folder=sent")

        // Symmetric: B's sent folder must contain B's email and never A's.
        @Suppress("UNCHECKED_CAST")
        val bSent = get("/api/mail?folder=sent", b.token).bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(bSent.any { it["id"] == bEmailId }, "Tenant B should see its own sent email")
        assertTrue(bSent.none { it["id"] == aEmailId }, "Tenant B must not see Tenant A's email in /mail?folder=sent")

        // Cross-tenant get-by-id is also blocked (the per-id endpoint runs a
        // JPQL query so the Hibernate tenant filter catches it — verify
        // anyway, since this is the most obvious IDOR vector).
        val bGetA = get("/api/mail/$aEmailId", b.token)
        assertTrue(
            bGetA.status() == 404 || bGetA.status() == 403,
            "Tenant B fetching Tenant A's email by id should return 404/403, got ${bGetA.status()}",
        )
    }
}
