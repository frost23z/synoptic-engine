package com.synopticengine.api.crm

import com.synopticengine.api.crm.quote.domain.Quote
import com.synopticengine.api.crm.quote.domain.QuoteItem
import com.synopticengine.api.crm.quote.service.quoteLineTotal
import com.synopticengine.api.crm.quote.service.toResponse
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals

/**
 * T4.4 — Monetary-precision round-trip tests.
 *
 * Verifies that [Quote.toResponse] produces correctly-scaled, HALF_UP-rounded
 * 2-decimal-place results across the full discount → tax → adjustment pipeline.
 *
 * All monetary arithmetic must use explicit scale + [RoundingMode.HALF_UP]; raw
 * [BigDecimal.divide] without a scale throws [ArithmeticException] on non-terminating
 * quotients (e.g. 100 / 3).
 *
 * Pure unit test — no Spring context, no database.
 */
class QuoteMonetaryPrecisionTest {
    // ── Helper to build an in-memory Quote without persistence ────────────────

    private fun buildQuote(
        discount: String,
        tax: String,
        adjustment: String,
        vararg items: QuoteItemSpec,
    ): Quote {
        val quote = Quote()
        // BaseEntity.id is a `val` assigned by @GeneratedValue; we must set it via
        // reflection for tests that bypass persistence.  Quote.toResponse() also
        // reads leadId (lateinit var) which must be initialised to avoid
        // UninitializedPropertyAccessException.
        setField(quote, "id", java.util.UUID.randomUUID())
        setField(quote, "leadId", java.util.UUID.randomUUID())
        setField(quote, "discount", BigDecimal(discount))
        setField(quote, "tax", BigDecimal(tax))
        setField(quote, "adjustment", BigDecimal(adjustment))

        items.forEach { spec ->
            val item = QuoteItem()
            setField(item, "id", java.util.UUID.randomUUID())
            setField(item, "quoteId", quote.id!!)
            setField(item, "quote", quote)
            setField(item, "quantity", spec.quantity)
            setField(item, "unitPrice", BigDecimal(spec.unitPrice))
            setField(item, "discount", BigDecimal(spec.discount))
            quote.items.add(item)
        }
        return quote
    }

    /** Simple DTO to pass item specs to [buildQuote]. */
    private data class QuoteItemSpec(
        val unitPrice: String,
        val quantity: Int = 1,
        val discount: String = "0.00",
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `grand total for a simple quote with no discounts is exact`() {
        // 2 × 50.00 = 100.00; no quote-level discount; 10% tax = 110.00
        val quote = buildQuote("0.00", "10.00", "0.00", QuoteItemSpec("50.00", quantity = 2))
        val response = quote.toResponse()

        assertEquals(bd("100.00"), response.subTotal)
        assertEquals(bd("110.00"), response.grandTotal)
    }

    @Test
    fun `33_33 percent quote-level discount rounds to 2dp with HALF_UP`() {
        // subTotal = 100.00
        // afterDiscount = 100.00 * (1 - 33.33/100) = 100.00 * 0.6667 = 66.67
        // grandTotal (no tax, no adjustment) = 66.67
        val quote = buildQuote("33.33", "0.00", "0.00", QuoteItemSpec("100.00"))
        val response = quote.toResponse()

        assertEquals(bd("100.00"), response.subTotal)
        assertEquals(bd("66.67"), response.grandTotal)
    }

    @Test
    fun `33_33 percent item discount AND quote tax both round correctly`() {
        // lineTotal = 100.00 * (1 - 33.33/100) = 66.67 (rounded from 66.6700…)
        // subTotal = 66.67
        // 10% quote tax on afterDiscount (no quote discount): 66.67 * 1.1 = 73.337 → 73.34
        val quote =
            buildQuote(
                "0.00",
                "10.00",
                "0.00",
                QuoteItemSpec("100.00", discount = "33.33"),
            )
        val response = quote.toResponse()

        assertEquals(bd("66.67"), response.subTotal)
        assertEquals(bd("73.34"), response.grandTotal)
    }

    @Test
    fun `compound discount plus tax matches manual HALF_UP calculation`() {
        // Items:
        //   item1: 200.00 × 2, 0% item discount  → lineTotal = 400.00
        //   item2: 150.00 × 1, 10% item discount → lineTotal = 135.00
        // subTotal = 535.00
        //
        // Quote: 5% discount, 8% tax, -25.00 adjustment (credit)
        //
        // afterDiscount = 535.00 × (1 − 0.05) = 535.00 × 0.95 = 508.25
        // taxAmount     = 508.25 × 0.08 = 40.66 (40.6600 → 40.66)
        // grandTotal    = 508.25 + 40.66 − 25.00 = 523.91
        val quote =
            buildQuote(
                "5.00",
                "8.00",
                "-25.00",
                QuoteItemSpec("200.00", quantity = 2, discount = "0.00"),
                QuoteItemSpec("150.00", quantity = 1, discount = "10.00"),
            )
        val response = quote.toResponse()

        assertEquals(bd("535.00"), response.subTotal)
        assertEquals(bd("523.91"), response.grandTotal)
    }

    @Test
    fun `high-precision percentage does not throw ArithmeticException`() {
        // 1/3 % = 0.333… — a non-terminating decimal that would throw without explicit scale
        val quote =
            buildQuote(
                "33.3333333333",
                "33.3333333333",
                "0.00",
                QuoteItemSpec("99.99"),
            )
        // Must not throw; result should be a 2dp BigDecimal
        val response = quote.toResponse()
        assertEquals(2, response.grandTotal.scale())
        assertEquals(2, response.subTotal.scale())
    }

    @Test
    fun `zero-value quote is exact at 0_00`() {
        val quote = buildQuote("0.00", "0.00", "0.00", QuoteItemSpec("0.00", quantity = 1))
        val response = quote.toResponse()
        assertEquals(bd("0.00"), response.grandTotal)
        assertEquals(bd("0.00"), response.subTotal)
    }

    @Test
    fun `adjustment increases grand total`() {
        // subTotal = 100.00, no discount, no tax, +15.00 adjustment
        val quote = buildQuote("0.00", "0.00", "15.00", QuoteItemSpec("100.00"))
        val response = quote.toResponse()
        assertEquals(bd("115.00"), response.grandTotal)
    }

    @Test
    fun `quoteLineTotal with non-terminating discount does not throw`() {
        // Regression guard: raw BigDecimal.divide(100) throws for 1/3, 2/3, etc.
        val result = quoteLineTotal(BigDecimal("100.00"), 1, BigDecimal("33.3333333333"))
        assertEquals(bd("66.67"), result.setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `item response line total is 2dp`() {
        val quote =
            buildQuote(
                "0.00",
                "0.00",
                "0.00",
                QuoteItemSpec("19.99", quantity = 3, discount = "5.00"),
            )
        val itemResponse = quote.toResponse().items.first()
        // 19.99 × 3 × 0.95 = 56.9715 → 56.97
        assertEquals(bd("56.97"), itemResponse.lineTotal)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun bd(value: String): BigDecimal = BigDecimal(value).setScale(2, RoundingMode.HALF_UP)

    /**
     * Set a field by name on an object, bypassing visibility — needed because
     * Quote/QuoteItem use `protected set` and JPA `lateinit var`.
     * Only used in tests to avoid a persistence layer for pure-unit precision checks.
     */
    private fun setField(
        target: Any,
        fieldName: String,
        value: Any?,
    ) {
        var clazz: Class<*>? = target::class.java
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        error("Field '$fieldName' not found on ${target::class.simpleName}")
    }
}
