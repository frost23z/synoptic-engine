package com.synopticengine.api.bootstrap

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.identity.domain.Role
import com.synopticengine.api.identity.domain.RoleType
import com.synopticengine.api.identity.repo.PermissionRepository
import com.synopticengine.api.identity.repo.RoleRepository
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.config.TenantSession
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BootstrapServiceIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var permissionRepository: PermissionRepository

    @Autowired
    lateinit var roleRepository: RoleRepository

    @Autowired
    lateinit var tenantSession: TenantSession

    @Test
    fun `permissions are seeded on startup`() {
        val names = permissionRepository.findAll().map { it.key }.toSet()
        assertTrue(names.isNotEmpty(), "No permissions seeded")
        assertTrue(names.any { it.contains("leads") }, "No leads permissions found")
        assertTrue(names.contains("leads.view"), "missing leads.view")
        assertTrue(names.contains("leads.edit"), "missing leads.edit")
        assertTrue(names.contains("leads.delete"), "missing leads.delete")
    }

    @Test
    @Transactional
    fun `default roles are seeded on startup`() {
        assertNotNull(findSeedRole("ADMIN"), "ADMIN role missing in seed tenant")
        assertNotNull(findSeedRole("MANAGER"), "MANAGER role missing in seed tenant")
        assertNotNull(findSeedRole("SALESPERSON"), "SALESPERSON role missing in seed tenant")
        assertNotNull(findSeedRole("VIEWER"), "VIEWER role missing in seed tenant")
    }

    @Test
    @Transactional
    fun `ADMIN role has all permissions`() {
        val admin = findSeedRole("ADMIN")!!
        // Phase 0 introduced RoleType.ALL: ADMIN holds no explicit permissions but bypasses
        // every check via the wildcard. Asserting on permissions.size would force every
        // future permission to be back-filled into ADMIN, which is exactly what ALL avoids.
        assertEquals(RoleType.ALL, admin.permissionType, "ADMIN should use the wildcard permissionType")
    }

    @Test
    @Transactional
    fun `SALESPERSON role has leads and contacts but not users delete`() {
        val sp = findSeedRole("SALESPERSON")!!
        val names = sp.permissions.map { it.key }.toSet()
        assertTrue(names.contains("leads.view"), "SALESPERSON missing leads.view")
        assertTrue(names.contains("contacts.view"), "SALESPERSON missing contacts.view")
        assertTrue(!names.contains("users.delete"), "SALESPERSON should not have users.delete")
    }

    // Roles are per-tenant (Phase 0). Other tests in the suite provision extra tenants, so
    // a bare findByName can return another tenant's role or trip the multiple-result guard.
    // Run under the seed tenant with the Hibernate filter enabled to pin the lookup.
    private fun findSeedRole(name: String): Role? =
        TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
            tenantSession.applyFilter()
            roleRepository.findByName(name)
        }
}
