package com.synopticengine.api.identity

import com.synopticengine.api.inventory.InventoryPermissionRegistry
import com.synopticengine.api.settings.SettingsPermissionRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class IdentityPermissionRegistryTest {
    @Test
    fun `identity registry contains expected keys`() {
        val keys = IdentityPermissionRegistry().permissions().map { it.key }
        assertTrue(keys.contains("users"))
        assertTrue(keys.contains("users.view"))
        assertTrue(keys.contains("users.edit"))
        assertTrue(keys.contains("users.delete"))
        assertTrue(keys.contains("roles"))
        assertTrue(keys.contains("roles.view"))
        assertTrue(keys.contains("roles.edit"))
        assertTrue(keys.contains("groups"))
        assertTrue(keys.contains("groups.view"))
    }

    @Test
    fun `inventory registry contains expected keys`() {
        val keys = InventoryPermissionRegistry().permissions().map { it.key }
        assertTrue(keys.contains("products"))
        assertTrue(keys.contains("products.view"))
        assertTrue(keys.contains("warehouses"))
        assertTrue(keys.contains("warehouses.view"))
    }

    @Test
    fun `settings registry contains expected keys`() {
        val keys = SettingsPermissionRegistry().permissions().map { it.key }
        assertTrue(keys.contains("settings"))
        assertTrue(keys.contains("settings.view"))
        assertTrue(keys.contains("attributes"))
        assertTrue(keys.contains("automations"))
        assertTrue(keys.contains("marketing"))
        assertTrue(keys.contains("imports"))
    }
}
