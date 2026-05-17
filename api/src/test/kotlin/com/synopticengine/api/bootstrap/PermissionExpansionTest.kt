package com.synopticengine.api.bootstrap

import com.synopticengine.api.identity.service.expandAuthorities
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionExpansionTest {
    private val allKnownKeys =
        setOf(
            "leads",
            "leads.view",
            "leads.edit",
            "leads.delete",
            "contacts",
            "contacts.view",
            "contacts.edit",
            "contacts.delete",
            "users",
            "users.view",
            "users.edit",
            "users.delete",
        )

    @Test
    fun `parent key expands to include all child keys`() {
        val expanded = expandAuthorities(listOf("leads"), allKnownKeys)
        assertTrue(expanded.contains("leads"), "parent key itself missing")
        assertTrue(expanded.contains("leads.view"), "leads.view missing")
        assertTrue(expanded.contains("leads.edit"), "leads.edit missing")
        assertTrue(expanded.contains("leads.delete"), "leads.delete missing")
    }

    @Test
    fun `child key does not expand to sibling or parent`() {
        val expanded = expandAuthorities(listOf("leads.view"), allKnownKeys)
        assertTrue(expanded.contains("leads.view"))
        assertFalse(expanded.contains("leads.edit"), "leads.edit should not be granted by leads.view")
        assertFalse(expanded.contains("leads"), "parent key should not be granted by child")
    }

    @Test
    fun `multiple keys expand independently`() {
        val expanded = expandAuthorities(listOf("leads", "users.view"), allKnownKeys)
        assertTrue(expanded.contains("leads.view"))
        assertTrue(expanded.contains("leads.edit"))
        assertTrue(expanded.contains("users.view"))
        assertFalse(expanded.contains("users.edit"), "users.edit should not be granted")
    }
}
