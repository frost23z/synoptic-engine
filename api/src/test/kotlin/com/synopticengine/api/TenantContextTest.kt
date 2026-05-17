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

    @Test
    fun `runAs sets within block and clears after when previously unset`() {
        val id = UUID.randomUUID()
        TenantContext.runAs(id) {
            assertEquals(id, TenantContext.get())
        }
        assertNull(TenantContext.get())
    }

    @Test
    fun `runAs restores previous value when nested`() {
        val outer = UUID.randomUUID()
        val inner = UUID.randomUUID()
        TenantContext.set(outer)
        TenantContext.runAs(inner) {
            assertEquals(inner, TenantContext.get())
        }
        assertEquals(outer, TenantContext.get())
    }

    @Test
    fun `runAs restores previous value even when block throws`() {
        val outer = UUID.randomUUID()
        val inner = UUID.randomUUID()
        TenantContext.set(outer)
        try {
            TenantContext.runAs(inner) {
                error("boom")
            }
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals(outer, TenantContext.get())
    }

    @Test
    fun `runAs returns the block result`() {
        val id = UUID.randomUUID()
        val result = TenantContext.runAs(id) { "hello" }
        assertEquals("hello", result)
    }
}
