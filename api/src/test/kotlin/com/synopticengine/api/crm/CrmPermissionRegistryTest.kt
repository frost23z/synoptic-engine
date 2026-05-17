package com.synopticengine.api.crm

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CrmPermissionRegistryTest {
    private val registry = CrmPermissionRegistry()

    @Test
    fun `registry contains expected CRM permission keys`() {
        val keys = registry.permissions().map { it.key }.toSet()
        assertTrue(keys.contains("leads"), "missing parent key: leads")
        assertTrue(keys.contains("leads.view"), "missing leads.view")
        assertTrue(keys.contains("leads.edit"), "missing leads.edit")
        assertTrue(keys.contains("leads.delete"), "missing leads.delete")
        assertTrue(keys.contains("contacts.view"), "missing contacts.view")
        assertTrue(keys.contains("activities.view"), "missing activities.view")
        assertTrue(keys.contains("quotes.view"), "missing quotes.view")
        assertTrue(keys.contains("mail.view"), "missing mail.view")
    }

    @Test
    fun `all permissions have crm module tag`() {
        val perms = registry.permissions()
        assertTrue(perms.isNotEmpty())
        assertTrue(perms.all { it.module == "crm" })
    }
}
