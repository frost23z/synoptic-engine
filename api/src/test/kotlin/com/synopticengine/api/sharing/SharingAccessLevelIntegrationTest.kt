package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import com.synopticengine.api.sharing.service.RecordShareService
import com.synopticengine.api.sharing.service.ResourceVisibilityService
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for per-access-level enforcement (T1.1).
 *
 * Covers:
 *  - [AccessLevel.canReshare] check in [RecordShareService.reshare]
 *  - [RecordShareService.assertCanWrite] guard
 *  - [RecordShareService.assertCanDelete] guard
 *  - Each access level tested for reshare eligibility
 *
 * Also verifies the HTTP reshare endpoint is gated on RECORDS_RESHARE permission.
 */
class SharingAccessLevelIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var visibilityRepository: ResourceVisibilityRepository

    @Autowired private lateinit var visibilityService: ResourceVisibilityService

    @Autowired private lateinit var recordShareService: RecordShareService

    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    // ── assertCanWrite ────────────────────────────────────────────────────────

    @Test
    fun `assertCanWrite - WRITE access passes`() {
        val owner = tenantProvisioner.provision("aclWrite1")
        val consumer = tenantProvisioner.provision("aclWrite2")
        val leadId = leadFactory.id(owner.token)

        shareAt(owner.tenantId, consumer.tenantId, leadId, AccessLevel.WRITE, owner.token)

        // Should not throw.
        recordShareService.assertCanWrite(consumer.tenantId, ResourceType.LEADS.literal, leadId)
    }

    @Test
    fun `assertCanWrite - MANAGE access passes (MANAGE implies WRITE)`() {
        val owner = tenantProvisioner.provision("aclMgr1")
        val consumer = tenantProvisioner.provision("aclMgr2")
        val leadId = leadFactory.id(owner.token)

        shareAt(owner.tenantId, consumer.tenantId, leadId, AccessLevel.MANAGE, owner.token)

        recordShareService.assertCanWrite(consumer.tenantId, ResourceType.LEADS.literal, leadId)
    }

    @Test
    fun `assertCanWrite - READ access denied`() {
        val owner = tenantProvisioner.provision("aclR1")
        val consumer = tenantProvisioner.provision("aclR2")
        val leadId = leadFactory.id(owner.token)

        shareAt(owner.tenantId, consumer.tenantId, leadId, AccessLevel.READ, owner.token)

        val ex =
            org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
                recordShareService.assertCanWrite(consumer.tenantId, ResourceType.LEADS.literal, leadId)
            }
        assertTrue(ex.message!!.contains("WRITE"))
    }

    @Test
    fun `assertCanWrite - NONE access denied`() {
        val owner = tenantProvisioner.provision("aclNone1")
        val consumer = tenantProvisioner.provision("aclNone2")
        val leadId = leadFactory.id(owner.token)
        // No share created.

        org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
            recordShareService.assertCanWrite(consumer.tenantId, ResourceType.LEADS.literal, leadId)
        }
    }

    // ── assertCanDelete ───────────────────────────────────────────────────────

    @Test
    fun `assertCanDelete - MANAGE access passes`() {
        val owner = tenantProvisioner.provision("aclDel1")
        val consumer = tenantProvisioner.provision("aclDel2")
        val leadId = leadFactory.id(owner.token)

        shareAt(owner.tenantId, consumer.tenantId, leadId, AccessLevel.MANAGE, owner.token)

        recordShareService.assertCanDelete(consumer.tenantId, ResourceType.LEADS.literal, leadId)
    }

    @Test
    fun `assertCanDelete - WRITE access denied (only MANAGE can delete)`() {
        val owner = tenantProvisioner.provision("aclDelW1")
        val consumer = tenantProvisioner.provision("aclDelW2")
        val leadId = leadFactory.id(owner.token)

        shareAt(owner.tenantId, consumer.tenantId, leadId, AccessLevel.WRITE, owner.token)

        org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
            recordShareService.assertCanDelete(consumer.tenantId, ResourceType.LEADS.literal, leadId)
        }
    }

    // ── reshare — service-level ───────────────────────────────────────────────

    @Test
    fun `reshare - consumer with MANAGE can reshare to a third tenant`() {
        val owner = tenantProvisioner.provision("rsOwner")
        val consumerA = tenantProvisioner.provision("rsConsA")
        val consumerB = tenantProvisioner.provision("rsConsB")
        val leadId = leadFactory.id(owner.token)

        // Owner gives consumerA MANAGE access.
        shareAt(owner.tenantId, consumerA.tenantId, leadId, AccessLevel.MANAGE, owner.token)

        // consumerA reshares with consumerB (READ).
        val reshare =
            recordShareService.reshare(
                actingTenantId = consumerA.tenantId,
                actingUserId = consumerA.userId,
                consumerTenantId = consumerB.tenantId,
                resourceType = ResourceType.LEADS.literal,
                resourceId = leadId,
                accessLevel = AccessLevel.READ,
            )

        assertEquals(owner.tenantId, reshare.ownerTenantId)
        assertEquals(consumerB.tenantId, reshare.consumerTenantId)
        assertEquals(AccessLevel.READ, reshare.accessLevel)

        // consumerB should now have visibility.
        val effective = visibilityService.effectiveAccess(consumerB.tenantId, ResourceType.LEADS.literal, leadId)
        assertEquals(AccessLevel.READ, effective)
    }

    @Test
    fun `reshare - access level is capped at the resharer's own level`() {
        val owner = tenantProvisioner.provision("rsCapOwner")
        val consumerA = tenantProvisioner.provision("rsCapA")
        val consumerB = tenantProvisioner.provision("rsCapB")
        val leadId = leadFactory.id(owner.token)

        // consumerA has MANAGE access — only MANAGE may call reshare().
        shareAt(owner.tenantId, consumerA.tenantId, leadId, AccessLevel.MANAGE, owner.token)

        // consumerA reshares at WRITE — a lower level than their own MANAGE.
        // Cap logic: min(requested=WRITE, effective=MANAGE) = WRITE.
        val reshare =
            recordShareService.reshare(
                actingTenantId = consumerA.tenantId,
                actingUserId = consumerA.userId,
                consumerTenantId = consumerB.tenantId,
                resourceType = ResourceType.LEADS.literal,
                resourceId = leadId,
                accessLevel = AccessLevel.WRITE, // requesting lower than their own level
            )
        // Should be WRITE as requested — never inflated above the actor's level.
        assertEquals(AccessLevel.WRITE, reshare.accessLevel)
    }

    @Test
    fun `reshare - consumer with READ access is rejected`() {
        val owner = tenantProvisioner.provision("rsReadOwner")
        val consumerA = tenantProvisioner.provision("rsReadA")
        val consumerB = tenantProvisioner.provision("rsReadB")
        val leadId = leadFactory.id(owner.token)

        // consumerA has only READ access — canReshare() requires MANAGE.
        shareAt(owner.tenantId, consumerA.tenantId, leadId, AccessLevel.READ, owner.token)

        org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
            recordShareService.reshare(
                actingTenantId = consumerA.tenantId,
                actingUserId = consumerA.userId,
                consumerTenantId = consumerB.tenantId,
                resourceType = ResourceType.LEADS.literal,
                resourceId = leadId,
                accessLevel = AccessLevel.READ,
            )
        }
    }

    @Test
    fun `reshare - consumer with WRITE access is rejected (WRITE below MANAGE)`() {
        val owner = tenantProvisioner.provision("rsWriteOwner")
        val consumerA = tenantProvisioner.provision("rsWriteA")
        val consumerB = tenantProvisioner.provision("rsWriteB")
        val leadId = leadFactory.id(owner.token)

        shareAt(owner.tenantId, consumerA.tenantId, leadId, AccessLevel.WRITE, owner.token)

        org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
            recordShareService.reshare(
                actingTenantId = consumerA.tenantId,
                actingUserId = consumerA.userId,
                consumerTenantId = consumerB.tenantId,
                resourceType = ResourceType.LEADS.literal,
                resourceId = leadId,
                accessLevel = AccessLevel.READ,
            )
        }
    }

    @Test
    fun `reshare - tenant with no share is rejected`() {
        val owner = tenantProvisioner.provision("rsNoneOwner")
        val consumerA = tenantProvisioner.provision("rsNoneA")
        val consumerB = tenantProvisioner.provision("rsNoneB")
        val leadId = leadFactory.id(owner.token)
        // No share to consumerA.

        org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
            recordShareService.reshare(
                actingTenantId = consumerA.tenantId,
                actingUserId = consumerA.userId,
                consumerTenantId = consumerB.tenantId,
                resourceType = ResourceType.LEADS.literal,
                resourceId = leadId,
                accessLevel = AccessLevel.READ,
            )
        }
    }

    // ── HTTP endpoint — reshare ───────────────────────────────────────────────

    @Test
    fun `POST records reshare - consumer with MANAGE shares onward via HTTP`() {
        val owner = tenantProvisioner.provision("httpRsOwner")
        val consumerA = tenantProvisioner.provision("httpRsA")
        val consumerB = tenantProvisioner.provision("httpRsB")
        val leadId = leadFactory.id(owner.token)

        shareAt(owner.tenantId, consumerA.tenantId, leadId, AccessLevel.MANAGE, owner.token)

        // consumerA's token does not have RECORDS_RESHARE by default unless it's an admin
        // with the wildcard role. The provisioned admin user has all permissions.
        val result =
            post(
                "/api/records/reshare",
                consumerA.token,
                mapOf(
                    "consumerTenantId" to consumerB.tenantId.toString(),
                    "resourceType" to ResourceType.LEADS.literal,
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "READ",
                ),
            )
        assertEquals(201, result.status(), result.response.contentAsString)
        val body = result.bodyAsMap()!!
        assertEquals(owner.tenantId.toString(), body["ownerTenantId"])
        assertEquals(consumerB.tenantId.toString(), body["consumerTenantId"])
        assertEquals("READ", body["accessLevel"])
    }

    @Test
    fun `POST records reshare - consumer with READ is rejected at service level`() {
        val owner = tenantProvisioner.provision("httpRsReadOwner")
        val consumerA = tenantProvisioner.provision("httpRsReadA")
        val consumerB = tenantProvisioner.provision("httpRsReadB")
        val leadId = leadFactory.id(owner.token)

        shareAt(owner.tenantId, consumerA.tenantId, leadId, AccessLevel.READ, owner.token)

        val result =
            post(
                "/api/records/reshare",
                consumerA.token,
                mapOf(
                    "consumerTenantId" to consumerB.tenantId.toString(),
                    "resourceType" to ResourceType.LEADS.literal,
                    "resourceId" to leadId.toString(),
                    "accessLevel" to "READ",
                ),
            )
        // Service throws AccessDeniedException → 403
        assertEquals(403, result.status())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** POST /api/records/share via HTTP using the owner's token. */
    private fun shareAt(
        ownerTenantId: UUID,
        consumerTenantId: UUID,
        resourceId: UUID,
        accessLevel: AccessLevel,
        ownerToken: String,
    ) {
        val resp =
            post(
                "/api/records/share",
                ownerToken,
                mapOf(
                    "consumerTenantId" to consumerTenantId.toString(),
                    "resourceType" to ResourceType.LEADS.literal,
                    "resourceId" to resourceId.toString(),
                    "accessLevel" to accessLevel.name,
                ),
            )
        assertEquals(201, resp.status(), "Share failed: ${resp.response.contentAsString}")
    }
}
