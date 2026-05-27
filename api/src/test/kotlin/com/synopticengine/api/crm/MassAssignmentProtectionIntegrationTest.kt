package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.shared.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * T4.5 — Mass-assignment protection.
 *
 * Verifies that no request body maps directly to a JPA entity. Specifically:
 *
 *  1. A `POST /api/leads` body that includes a foreign `tenantId` field is accepted
 *     without a 400/422 — the unknown field is silently ignored by Jackson (the DTO
 *     does not declare `tenantId`).
 *  2. The entity is persisted with the seed tenant's `tenantId` (from [TenantContext]),
 *     NOT with the attacker-supplied UUID from the request body.
 *
 * This protects against an attacker crossing the tenant boundary by including
 * sensitive fields (`tenantId`, `id`, `createdAt`, `version`) in a JSON payload.
 *
 * Integration test — requires a running Spring context + database (Testcontainers).
 */
class MassAssignmentProtectionIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var leadRepository: LeadRepository

    private lateinit var adminToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    @Test
    fun `POST with attacker-supplied tenantId is accepted and tenantId is ignored`() {
        val attackerTenantId = UUID.randomUUID()

        // Include 'tenantId' — a field that exists on BaseEntity but NOT on CreateLeadRequest.
        // Jackson should silently ignore it (DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false).
        val payload =
            mapOf(
                "title" to "Mass-assign attack attempt",
                "tenantId" to attackerTenantId.toString(),
            )

        val result = post("/api/leads", adminToken, payload)
        assertEquals(201, result.status(), "Request should succeed; unknown fields must be ignored, not rejected")

        val body = result.bodyAsMap()!!
        val leadId = UUID.fromString(body["id"] as String)

        // Verify the entity was persisted with the seed tenant — never the attacker's UUID.
        val saved =
            TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
                leadRepository.findActiveById(leadId)
            }
        assertNotNull(saved, "Lead should have been created")
        assertEquals(
            TenantContext.SEED_TENANT_ID,
            saved.tenantId,
            "tenantId must come from TenantContext, not from the request body",
        )
        assertNotEquals(
            attackerTenantId,
            saved.tenantId,
            "Attacker-supplied tenantId must not be persisted",
        )
    }

    @Test
    fun `POST with attacker-supplied id is accepted and id is ignored`() {
        val attackerId = UUID.randomUUID()

        val payload =
            mapOf(
                "title" to "Mass-assign id attack",
                "id" to attackerId.toString(),
            )

        val result = post("/api/leads", adminToken, payload)
        assertEquals(201, result.status(), "Unknown field 'id' must not cause a 400/422")

        val body = result.bodyAsMap()!!
        val returnedId = UUID.fromString(body["id"] as String)

        // The server-generated UUID must differ from the attacker-supplied one.
        assertNotEquals(
            attackerId,
            returnedId,
            "The server must generate the entity ID, ignoring any 'id' field in the request body",
        )
    }

    @Test
    fun `POST with attacker-supplied version and createdAt is accepted without error`() {
        // 'version' and 'createdAt' are sensitive audit fields from BaseEntity /
        // AuditableEntity; they must not be accepted from request bodies.
        val payload =
            mapOf(
                "title" to "Audit-field injection attempt",
                "version" to 9999,
                "createdAt" to "2000-01-01T00:00:00Z",
                "updatedAt" to "2000-01-01T00:00:00Z",
                "deletedAt" to "2000-01-01T00:00:00Z",
            )

        val result = post("/api/leads", adminToken, payload)
        assertEquals(201, result.status(), "Audit/lifecycle fields in body must be silently ignored")

        val body = result.bodyAsMap()!!
        // 'deletedAt' must not appear in the lead response (entity is not soft-deleted)
        // and 'version' must be 0 (first save), not 9999.
        val savedId = UUID.fromString(body["id"] as String)
        val saved =
            TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
                leadRepository.findActiveById(savedId)
            }
        assertNotNull(saved)
        assertEquals(0L, saved.version, "version must be auto-managed, not injectable")
        assertEquals(null, saved.deletedAt, "deletedAt must not be injectable — lead should not appear soft-deleted")
    }
}
