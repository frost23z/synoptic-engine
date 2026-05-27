package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * T3.5 — Role elevation guard.
 *
 * A non-ADMIN user must not be able to assign an ADMIN role to themselves or
 * to another user. Without this guard a salesperson with `users.create` and
 * `users.edit` permissions could escalate any account — including their own —
 * to full admin.
 */
class RoleElevationIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Test
    fun `non-admin cannot create a user with ADMIN role via POST`() {
        val t = tenantProvisioner.provision("role-elev-create-${UUID.randomUUID().toString().take(6)}")

        // Create a limited role with only users.create — no wildcard authority.
        val adminToken = t.token
        val limitedRoleName = "NOADMIN_${UUID.randomUUID().toString().take(6)}"
        val createRole =
            post(
                "/api/roles",
                adminToken,
                mapOf("name" to limitedRoleName, "permissions" to listOf("users.create", "users.edit")),
            )
        assertEquals(201, createRole.response.status, "Role creation failed: ${createRole.response.contentAsString}")

        val limitedToken = tokenFor(setOf(limitedRoleName), t.tenantId)

        // A user with limitedToken tries to create a new user with the ADMIN role.
        val result =
            post(
                "/api/users",
                limitedToken,
                mapOf(
                    "email" to "attacker-${UUID.randomUUID().toString().take(6)}@example.com",
                    "password" to "Password123!",
                    "firstName" to "Hacker",
                    "lastName" to "McHack",
                    "roles" to setOf("ADMIN"),
                ),
            )
        assertEquals(
            403,
            result.response.status,
            "Expected 403 when non-ADMIN assigns ADMIN role, got ${result.response.status}: ${result.response.contentAsString}",
        )
    }

    @Test
    fun `non-admin cannot update a user to ADMIN role via PUT`() {
        val t = tenantProvisioner.provision("role-elev-update-${UUID.randomUUID().toString().take(6)}")
        val adminToken = t.token

        // Create a limited role.
        val limitedRoleName = "NOADMIN_UPDATE_${UUID.randomUUID().toString().take(6)}"
        val createRole =
            post(
                "/api/roles",
                adminToken,
                mapOf("name" to limitedRoleName, "permissions" to listOf("users.create", "users.edit")),
            )
        assertEquals(201, createRole.response.status)

        // Create a target user with the limited role.
        val targetCreate =
            post(
                "/api/users",
                adminToken,
                mapOf(
                    "email" to "target-${UUID.randomUUID().toString().take(6)}@example.com",
                    "password" to "Password123!",
                    "firstName" to "Target",
                    "lastName" to "User",
                    "roles" to setOf(limitedRoleName),
                ),
            )
        assertEquals(201, targetCreate.response.status)
        @Suppress("UNCHECKED_CAST")
        val targetId = (targetCreate.bodyAsMap()?.get("id") as String)
        assertNotNull(targetId)

        val limitedToken = tokenFor(setOf(limitedRoleName), t.tenantId)

        // Non-admin tries to promote target to ADMIN.
        val result =
            put(
                "/api/users/$targetId",
                limitedToken,
                mapOf(
                    "firstName" to "Target",
                    "lastName" to "User",
                    "roles" to setOf("ADMIN"),
                ),
            )
        assertEquals(
            403,
            result.response.status,
            "Expected 403 when non-ADMIN promotes user to ADMIN, got ${result.response.status}",
        )
    }

    @Test
    fun `admin CAN create a user with ADMIN role`() {
        val t = tenantProvisioner.provision("role-elev-admin-ok-${UUID.randomUUID().toString().take(6)}")
        val adminToken = t.token

        val result =
            post(
                "/api/users",
                adminToken,
                mapOf(
                    "email" to "newadmin-${UUID.randomUUID().toString().take(6)}@example.com",
                    "password" to "Password123!",
                    "firstName" to "New",
                    "lastName" to "Admin",
                    "roles" to setOf("ADMIN"),
                ),
            )
        assertEquals(
            201,
            result.response.status,
            "ADMIN should be allowed to create another ADMIN user, got ${result.response.status}: ${result.response.contentAsString}",
        )
    }
}
