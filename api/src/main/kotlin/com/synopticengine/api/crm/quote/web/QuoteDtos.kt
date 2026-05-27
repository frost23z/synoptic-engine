package com.synopticengine.api.crm.quote.web

import com.synopticengine.api.crm.quote.domain.QuoteStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class QuoteItemRequest(
    val productId: UUID? = null,
    @field:Min(1)
    val quantity: Int = 1,
    @field:NotNull(message = "Unit price is required")
    @field:DecimalMin(value = "0.00", message = "Unit price must be non-negative")
    val unitPrice: BigDecimal,
    @field:DecimalMin(value = "0.00", message = "Discount must be non-negative")
    @field:DecimalMax(value = "100.00", message = "Discount must not exceed 100%")
    val discount: BigDecimal = BigDecimal.ZERO,
)

data class CreateQuoteRequest(
    @field:NotNull(message = "Lead is required")
    val leadId: UUID,
    @field:NotBlank(message = "Title is required")
    val title: String,
    val userId: UUID? = null,
    val personId: UUID? = null,
    @field:DecimalMin(value = "0.00", message = "Discount must be non-negative")
    @field:DecimalMax(value = "100.00", message = "Discount must not exceed 100%")
    val discount: BigDecimal = BigDecimal.ZERO,
    @field:DecimalMin(value = "0.00", message = "Tax must be non-negative")
    @field:DecimalMax(value = "100.00", message = "Tax must not exceed 100%")
    val tax: BigDecimal = BigDecimal.ZERO,
    val adjustment: BigDecimal = BigDecimal.ZERO,
    @field:Size(max = 5_000, message = "Terms must not exceed 5 000 characters")
    val terms: String? = null,
    val expiredAt: LocalDate? = null,
    val billingAddress: Map<String, Any?>? = null,
    val shippingAddress: Map<String, Any?>? = null,
    @field:Valid
    @field:Size(max = 500, message = "Cannot add more than 500 line items")
    val items: List<QuoteItemRequest> = emptyList(),
)

data class UpdateQuoteRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val userId: UUID? = null,
    val personId: UUID? = null,
    @field:DecimalMin(value = "0.00", message = "Discount must be non-negative")
    @field:DecimalMax(value = "100.00", message = "Discount must not exceed 100%")
    val discount: BigDecimal = BigDecimal.ZERO,
    @field:DecimalMin(value = "0.00", message = "Tax must be non-negative")
    @field:DecimalMax(value = "100.00", message = "Tax must not exceed 100%")
    val tax: BigDecimal = BigDecimal.ZERO,
    val adjustment: BigDecimal = BigDecimal.ZERO,
    @field:Size(max = 5_000, message = "Terms must not exceed 5 000 characters")
    val terms: String? = null,
    val expiredAt: LocalDate? = null,
    val billingAddress: Map<String, Any?>? = null,
    val shippingAddress: Map<String, Any?>? = null,
    @field:Valid
    @field:Size(max = 500, message = "Cannot add more than 500 line items")
    val items: List<QuoteItemRequest> = emptyList(),
)

data class UpdateQuoteStatusRequest(
    @field:NotNull(message = "Status is required")
    val status: QuoteStatus,
)

data class MassDestroyQuoteRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot delete more than $MAX_BATCH_SIZE quotes at once")
    val ids: List<UUID>,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

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
    val personId: UUID?,
    val title: String,
    val status: String,
    val discount: BigDecimal,
    val tax: BigDecimal,
    val adjustment: BigDecimal,
    val terms: String?,
    val expiredAt: LocalDate?,
    val billingAddress: Map<String, Any?>?,
    val shippingAddress: Map<String, Any?>?,
    val items: List<QuoteItemResponse>,
    val subTotal: BigDecimal,
    val grandTotal: BigDecimal,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class SendQuoteMailRequest(
    @field:NotBlank(message = "Recipient address is required")
    @field:Email(message = "Invalid recipient email address")
    val to: String,
    @field:Size(max = 500, message = "Subject must not exceed 500 characters")
    val subject: String? = null,
    @field:Size(max = 5_000, message = "Message must not exceed 5 000 characters")
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
