package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.identity.repo.RoleRepository
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.config.TenantSession
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertTrue

/**
 * P1.8 acceptance: V033 backfills `.create` keys onto every CUSTOM role that already
 * had the matching `.edit` key. Verifies both seed-tenant roles and any role created
 * by the bootstrap allowlist filter (which already starts with `.` patterns matching
 * the new keys).
 */
class CreatePermissionBackfillIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var roleRepository: RoleRepository

    @Autowired
    lateinit var tenantSession: TenantSession

    @Test
    @Transactional
    fun `seeded SALESPERSON role gains leads create activities create and quotes create`() {
        val sp = findSeedRole("SALESPERSON")!!
        val keys = sp.permissions.map { it.key }.toSet()

        assertTrue(keys.contains("leads.create"), "SALESPERSON should have leads.create after backfill")
        assertTrue(keys.contains("activities.create"), "SALESPERSON should have activities.create")
        assertTrue(keys.contains("quotes.create"), "SALESPERSON should have quotes.create")
        assertTrue(keys.contains("contacts.create"), "SALESPERSON should have contacts.create")
        // Pre-existing keys remain.
        assertTrue(keys.contains("leads.edit"))
        assertTrue(keys.contains("leads.view"))
        // Still no destructive keys.
        assertTrue(!keys.contains("leads.delete"), "SALESPERSON must not have leads.delete")
    }

    @Test
    @Transactional
    fun `MANAGER role retains the new create keys it gets via the wildcard allowlist`() {
        val manager = findSeedRole("MANAGER")!!
        val keys = manager.permissions.map { it.key }.toSet()

        // MANAGER's bootstrap filter is "every key except users.delete and roles.edit", so
        // every newly seeded .create key flows in automatically.
        assertTrue(keys.contains("leads.create"))
        assertTrue(keys.contains("products.create"))
        assertTrue(keys.contains("warehouses.create"))
    }

    private fun findSeedRole(name: String) =
        TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
            tenantSession.applyFilter()
            roleRepository.findByName(name)
        }
}
