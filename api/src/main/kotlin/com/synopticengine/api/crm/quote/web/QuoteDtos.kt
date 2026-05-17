package com.synopticengine.api.crm.quote.web

import com.synopticengine.api.crm.quote.domain.QuoteStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class QuoteItemRequest(
    val productId: UUID? = null,
    @field:Min(1)
    val quantity: Int = 1,
    @field:NotNull
    val unitPrice: BigDecimal,
    val discount: BigDecimal = BigDecimal.ZERO,
)

data class CreateQuoteRequest(
    @field:NotNull(message = "Lead is required")
    val leadId: UUID,
    @field:NotBlank(message = "Title is required")
    val title: String,
    val userId: UUID? = null,
    val discount: BigDecimal = BigDecimal.ZERO,
    val tax: BigDecimal = BigDecimal.ZERO,
    val terms: String? = null,
    val expiredAt: LocalDate? = null,
    @field:Valid
    val items: List<QuoteItemRequest> = emptyList(),
)

data class UpdateQuoteRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val userId: UUID? = null,
    val discount: BigDecimal = BigDecimal.ZERO,
    val tax: BigDecimal = BigDecimal.ZERO,
    val terms: String? = null,
    val expiredAt: LocalDate? = null,
    @field:Valid
    val items: List<QuoteItemRequest> = emptyList(),
)

data class UpdateQuoteStatusRequest(
    val status: QuoteStatus,
)

data class MassDestroyQuoteRequest(
    val ids: List<UUID>,
)

data class QuoteItemResponse(
    val id: UUID,
    val productId: UUID?,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val discount: BigDecimal,
    val lineTotal: BigDecimal,
)

data class QuoteResponse(
    val id: UUID,
    val leadId: UUID,
    val userId: UUID?,
    val title: String,
    val status: String,
    val discount: BigDecimal,
    val tax: BigDecimal,
    val terms: String?,
    val expiredAt: LocalDate?,
    val items: List<QuoteItemResponse>,
    val subTotal: BigDecimal,
    val grandTotal: BigDecimal,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class SendQuoteMailRequest(
    val to: String,
    val subject: String? = null,
    val message: String? = null,
)

data class SendQuoteMailResponse(
    val sent: Boolean,
    val message: String,
)

data class QuoteLeadProductResponse(
    val productId: UUID,
    val name: String,
    val sku: String?,
    val quantity: Int,
    val price: BigDecimal,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val taxAmount: BigDecimal = BigDecimal.ZERO,
)
