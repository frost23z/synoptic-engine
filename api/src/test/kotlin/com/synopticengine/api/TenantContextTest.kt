package com.synopticengine.api

import com.synopticengine.api.shared.TenantContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TenantContextTest {
    @AfterEach
    fun cleanup() = TenantContext.clear()

    @Test
    fun `get returns null when not set`() {
        assertNull(TenantContext.get())
    }

    @Test
    fun `set and get returns the same UUID`() {
        val id = UUID.randomUUID()
        TenantContext.set(id)
        assertEquals(id, TenantContext.get())
    }

    @Test
    fun `clear removes the value`() {
        TenantContext.set(UUID.randomUUID())
        TenantContext.clear()
        assertNull(TenantContext.get())
    }
}
