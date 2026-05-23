package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.CrossTenantAction
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.repo.CrossTenantAuditRepository
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.OrganizationFactory
import com.synopticengine.api.support.factories.PersonFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 2 / Sprint 2c: per-record shares. Each share writes a `record_shares` row,
 * a direct `resource_visibility` row, and (for leads) cascade rows for the related
 * person/organization per `CascadeRules`.
 */
class RecordShareIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var visibilityRepository: ResourceVisibilityRepository

    @Autowired private lateinit var auditRepository: CrossTenantAuditRepository

    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var personFactory: PersonFactory

    @Autowired private lateinit var organizationFactory: OrganizationFactory

    @Autowired private lateinit var leadFactory: LeadFactory

    @Test
    fun `share a lead with a partner — direct visibility plus cascade to person and organization`() {
        val owner = tenantProvisioner.provision("owner")
        val consumer = tenantProvisioner.provision("consumer")

        val personId = personFactory.id(owner.token, firstName = "Alice", lastName = "Smith")
        val organizationId = organizationFactory.id(owner.token, name = "Acme Inc")
        val leadId =
            UUID.fromString(
                leadFactory.create(
                    owner.token,
                    title = "Acme — ad hoc share",
                    personId = personId,
                    organizationId = organizationId,
                    amount = 25_000,
                )["id"] as String,
            )

        val shareResp =
            post(
                "/api/records/share",
                owner.token,
                mapOf(
                    "consumerTenantId" to consumer.tenantId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "WRITE",
                ),
            )
        assertEquals(201, shareResp.status(), shareResp.response.contentAsString)
        val shareId = UUID.fromString(shareResp.bodyAsMap()!!["id"] as String)

        // Direct visibility for the lead, cascade for the person + organization.
        assertEquals(AccessLevel.WRITE, visibilityFor(consumer.tenantId, ResourceType.LEADS, leadId))
        assertEquals(AccessLevel.READ, visibilityFor(consumer.tenantId, ResourceType.PERSONS, personId))
        assertEquals(AccessLevel.READ, visibilityFor(consumer.tenantId, ResourceType.ORGANIZATIONS, organizationId))

        val list = get("/api/records/leads/$leadId/shares", owner.token).bodyAsList()!!
        assertTrue(list.any { it["id"] == shareId.toString() })
        assertTrue(get("/api/records/shared-with-me", consumer.token).bodyAsList()!!.any { it["id"] == shareId.toString() })

        // Revoke: every visibility row attached to this share disappears.
        val consumerUserId = UUID.fromString(get("/auth/me", consumer.token).bodyAsMap()!!["id"] as String)
        val revoked = delete("/api/records/share/$shareId", consumer.token)
        assertEquals(200, revoked.status())
        assertNotNull(revoked.bodyAsMap()!!["revokedAt"])

        assertEquals(AccessLevel.NONE, visibilityFor(consumer.tenantId, ResourceType.LEADS, leadId))
        assertEquals(AccessLevel.NONE, visibilityFor(consumer.tenantId, ResourceType.PERSONS, personId))
        assertEquals(AccessLevel.NONE, visibilityFor(consumer.tenantId, ResourceType.ORGANIZATIONS, organizationId))
        assertFalse(
            get("/api/records/leads/$leadId/shares", owner.token).bodyAsList()!!.any {
                it["id"] ==
                    shareId.toString()
            },
        )
        assertTrue(get("/api/records/shared-with-me", consumer.token).bodyAsList()!!.none { it["id"] == shareId.toString() })

        val revokeRows = auditRepository.findAll().filter { it.action == CrossTenantAction.REVOKE && it.resourceId == leadId }
        assertTrue(revokeRows.isNotEmpty(), "Expected a REVOKE audit row")
        val latest = revokeRows.maxByOrNull { it.at ?: java.time.Instant.EPOCH }!!
        assertEquals(owner.tenantId, latest.ownerTenantId)
        assertEquals(consumer.tenantId, latest.actorTenantId)
        assertEquals(consumerUserId, latest.actorUserId)
    }

    @Test
    fun `cannot share a lead that isn't owned by your tenant`() {
        val attempting = tenantProvisioner.provision("attA")
        val other = tenantProvisioner.provision("attB")

        // Lead lives in the OTHER tenant.
        val leadId = leadFactory.id(other.token, title = "Their lead")

        val attempt =
            post(
                "/api/records/share",
                attempting.token,
                mapOf(
                    "consumerTenantId" to other.tenantId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "READ",
                ),
            )
        // We deliberately don't leak existence through 403 — the lead is invisible
        // to the acting tenant via Hibernate filter, so 404 is also valid.
        assertTrue(attempt.status() == 404 || attempt.status() == 403, "Got ${attempt.status()}")
    }

    @Test
    fun `re-sharing the same record upgrades access level instead of duplicating`() {
        val owner = tenantProvisioner.provision("dupOwner")
        val consumer = tenantProvisioner.provision("dupConsumer")
        val leadId = leadFactory.id(owner.token, title = "Dup lead")

        val firstId =
            post(
                "/api/records/share",
                owner.token,
                mapOf(
                    "consumerTenantId" to consumer.tenantId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "READ",
                ),
            ).bodyAsMap()!!["id"]

        val secondId =
            post(
                "/api/records/share",
                owner.token,
                mapOf(
                    "consumerTenantId" to consumer.tenantId.toString(),
                    "resourceType" to "leads",
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "WRITE",
                ),
            ).bodyAsMap()!!["id"]

        assertEquals(firstId, secondId, "Re-sharing the same record should update the existing row, not insert another")
        assertEquals(AccessLevel.WRITE, visibilityFor(consumer.tenantId, ResourceType.LEADS, leadId))
    }

    private fun visibilityFor(
        consumerTenantId: UUID,
        resourceType: ResourceType,
        resourceId: UUID,
    ): AccessLevel {
        val rows =
            visibilityRepository.findAllByConsumerTenantIdAndResourceTypeAndResourceId(
                consumerTenantId,
                resourceType.literal,
                resourceId,
            )
        if (rows.isEmpty()) return AccessLevel.NONE
        return rows.map { it.accessLevel }.reduce(AccessLevel::max)
    }
}
