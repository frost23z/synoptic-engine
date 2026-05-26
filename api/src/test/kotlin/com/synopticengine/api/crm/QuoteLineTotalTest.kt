package com.synopticengine.api.crm

import com.synopticengine.api.crm.quote.service.quoteLineTotal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals

/**
 * Pure unit tests (no Spring / DB) for the quote line-total money math (T0.1).
 * The discount division carries an explicit scale + [RoundingMode] so it rounds
 * deterministically and can never throw on awkward ratios.
 */
class QuoteLineTotalTest {
    @Test
    fun `33_33 percent line discount returns a rounded value without throwing`() {
        val total = assertDoesNotThrow { quoteLineTotal(BigDecimal("100.00"), 1, BigDecimal("33.33")) }
        assertEquals(BigDecimal("66.67"), total.setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `high-precision discount does not throw and rounds to two places`() {
        val total = assertDoesNotThrow { quoteLineTotal(BigDecimal("100"), 1, BigDecimal("33.3333333333")) }
        assertEquals(BigDecimal("66.67"), total.setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `zero discount equals unit price times quantity`() {
        val total = quoteLineTotal(BigDecimal("250.00"), 3, BigDecimal.ZERO)
        assertEquals(BigDecimal("750.00"), total.setScale(2, RoundingMode.HALF_UP))
    }
}
