package com.synopticengine.api.bootstrap

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.identity.repo.PermissionRepository
import com.synopticengine.api.identity.repo.RoleRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BootstrapServiceIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var permissionRepository: PermissionRepository

    @Autowired
    lateinit var roleRepository: RoleRepository

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
    fun `default roles are seeded on startup`() {
        assertNotNull(roleRepository.findByName("ADMIN"), "ADMIN role missing")
        assertNotNull(roleRepository.findByName("MANAGER"), "MANAGER role missing")
        assertNotNull(roleRepository.findByName("SALESPERSON"), "SALESPERSON role missing")
        assertNotNull(roleRepository.findByName("VIEWER"), "VIEWER role missing")
    }

    @Test
    @Transactional
    fun `ADMIN role has all permissions`() {
        val admin = roleRepository.findByName("ADMIN")!!
        assertTrue(admin.permissions.isNotEmpty(), "ADMIN has no permissions")
    }

    @Test
    @Transactional
    fun `SALESPERSON role has leads and contacts but not users delete`() {
        val sp = roleRepository.findByName("SALESPERSON")!!
        val names = sp.permissions.map { it.key }.toSet()
        assertTrue(names.contains("leads.view"), "SALESPERSON missing leads.view")
        assertTrue(names.contains("contacts.view"), "SALESPERSON missing contacts.view")
        assertTrue(!names.contains("users.delete"), "SALESPERSON should not have users.delete")
    }
}
