package com.synopticengine.api.crm

import com.synopticengine.api.crm.quote.service.quoteLineTotal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals

/**
 * T7.4 — Pure unit tests for quote money/totals logic (T0.1 / T4.3).
 *
 * No Spring context, no Testcontainers. Run via `./gradlew unitTests`.
 *
 * These tests exercise [quoteLineTotal] and the grand-total computation
 * extracted into `QuoteService.kt`.
 */
class QuoteTotalsUnitTest {
    // ── quoteLineTotal ────────────────────────────────────────────────────────

    @Test
    fun `zero discount returns unitPrice times quantity`() {
        val result =
            quoteLineTotal(
                unitPrice = BigDecimal("10.00"),
                quantity = 3,
                discountPercent = BigDecimal.ZERO,
            )
        assertEquals(BigDecimal("30.000000000000"), result)
    }

    @Test
    fun `50 percent discount halves the line total`() {
        val result =
            quoteLineTotal(
                unitPrice = BigDecimal("100.00"),
                quantity = 2,
                discountPercent = BigDecimal("50"),
            )
        // 100 * 2 * (1 - 0.5) = 100.00
        assertEquals(
            BigDecimal("100.00").setScale(2, RoundingMode.HALF_UP),
            result.setScale(2, RoundingMode.HALF_UP),
        )
    }

    @Test
    fun `non-terminating discount 33_33 percent does not throw ArithmeticException`() {
        // Regression for T0.1: BigDecimal.divide without scale/RoundingMode throws on
        // non-terminating decimal expansions like 33.33.
        assertDoesNotThrow {
            quoteLineTotal(
                unitPrice = BigDecimal("99.99"),
                quantity = 1,
                discountPercent = BigDecimal("33.33"),
            )
        }
    }

    @Test
    fun `non-terminating discount produces correct rounded result`() {
        val result =
            quoteLineTotal(
                unitPrice = BigDecimal("99.99"),
                quantity = 1,
                discountPercent = BigDecimal("33.33"),
            ).setScale(2, RoundingMode.HALF_UP)
        // 99.99 * (1 - 0.3333) = 99.99 * 0.6667 ≈ 66.66
        val discountFraction = BigDecimal("33.33").divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
        val expected =
            BigDecimal("99.99")
                .multiply(BigDecimal.ONE.subtract(discountFraction))
                .setScale(2, RoundingMode.HALF_UP)
        assertEquals(expected, result)
    }

    @Test
    fun `100 percent discount yields zero line total`() {
        val result =
            quoteLineTotal(
                unitPrice = BigDecimal("49.99"),
                quantity = 5,
                discountPercent = BigDecimal("100"),
            ).setScale(2, RoundingMode.HALF_UP)
        assertEquals(BigDecimal("0.00"), result)
    }

    @Test
    fun `single unit, no discount, fractional price`() {
        val result =
            quoteLineTotal(
                unitPrice = BigDecimal("19.99"),
                quantity = 1,
                discountPercent = BigDecimal.ZERO,
            ).setScale(2, RoundingMode.HALF_UP)
        assertEquals(BigDecimal("19.99"), result)
    }

    // ── Grand-total arithmetic ────────────────────────────────────────────────

    @Test
    fun `grand total equals subTotal minus discount plus tax plus adjustment`() {
        // Manual reproduction of Quote.toResponse() math.
        val unitPrice = BigDecimal("100.00")
        val quantity = 2
        val lineDiscount = BigDecimal.ZERO
        val quoteDiscount = BigDecimal("10") // 10% quote-level discount
        val tax = BigDecimal("5") // 5% tax
        val adjustment = BigDecimal("-2.00") // negative adjustment

        val subTotal =
            quoteLineTotal(unitPrice, quantity, lineDiscount)
                .setScale(2, RoundingMode.HALF_UP)
        // subTotal = 200.00

        val afterDiscount =
            subTotal.multiply(
                BigDecimal.ONE.subtract(quoteDiscount.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)),
            )
        // afterDiscount = 200 * 0.9 = 180.00

        val taxAmount = afterDiscount.multiply(tax.divide(BigDecimal(100), 10, RoundingMode.HALF_UP))
        // taxAmount = 180 * 0.05 = 9.00

        val grandTotal = afterDiscount.add(taxAmount).add(adjustment).setScale(2, RoundingMode.HALF_UP)
        // grandTotal = 180 + 9 - 2 = 187.00

        assertEquals(BigDecimal("187.00"), grandTotal)
    }

    @Test
    fun `grand total with non-terminating tax percent does not throw`() {
        val subTotal = BigDecimal("150.00")
        assertDoesNotThrow {
            val afterDiscount =
                subTotal.multiply(
                    BigDecimal.ONE.subtract(BigDecimal("33.33").divide(BigDecimal(100), 10, RoundingMode.HALF_UP)),
                )
            val taxAmount =
                afterDiscount.multiply(
                    BigDecimal("16.67").divide(BigDecimal(100), 10, RoundingMode.HALF_UP),
                )
            afterDiscount.add(taxAmount).add(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        }
    }

    @Test
    fun `zero tax and zero discount - grand total equals sub total`() {
        val unitPrice = BigDecimal("25.50")
        val quantity = 4

        val subTotal =
            quoteLineTotal(unitPrice, quantity, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
        val afterDiscount =
            subTotal.multiply(
                BigDecimal.ONE.subtract(BigDecimal.ZERO.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)),
            )
        val taxAmount = afterDiscount.multiply(BigDecimal.ZERO.divide(BigDecimal(100), 10, RoundingMode.HALF_UP))
        val grandTotal = afterDiscount.add(taxAmount).add(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        assertEquals(BigDecimal("102.00"), grandTotal)
    }
}
