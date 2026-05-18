package com.synopticengine.api.sharing

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SharingPermissionRegistryTest {
    @Test
    fun `sharing registry contains expected keys`() {
        val keys = SharingPermissionRegistry().permissions().map { it.key }
        assertTrue(keys.contains("relationships"))
        assertTrue(keys.contains("relationships.view"))
        assertTrue(keys.contains("relationships.manage"))
        assertTrue(keys.contains("share-policies"))
        assertTrue(keys.contains("share-policies.view"))
        assertTrue(keys.contains("share-policies.manage"))
        assertTrue(keys.contains("records"))
        assertTrue(keys.contains("records.share"))
        assertTrue(keys.contains("records.reshare"))
    }
}
