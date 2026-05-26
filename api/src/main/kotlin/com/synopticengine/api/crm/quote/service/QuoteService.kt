package com.synopticengine.api.crm.quote.service

import com.synopticengine.api.crm.lead.domain.LeadProduct
import com.synopticengine.api.crm.lead.repo.LeadProductRepository
import com.synopticengine.api.crm.quote.domain.Quote
import com.synopticengine.api.crm.quote.domain.QuoteItem
import com.synopticengine.api.crm.quote.domain.QuoteStatus
import com.synopticengine.api.crm.quote.repo.QuoteRepository
import com.synopticengine.api.crm.quote.web.QuoteItemRequest
import com.synopticengine.api.crm.quote.web.QuoteItemResponse
import com.synopticengine.api.crm.quote.web.QuoteLeadProductResponse
import com.synopticengine.api.crm.quote.web.QuoteResponse
import com.synopticengine.api.crm.quote.web.SendQuoteMailResponse
import com.synopticengine.api.crm.scoping.ScopeResolver
import com.synopticengine.api.shared.DomainEvent
import com.synopticengine.api.shared.email.MailSenderService
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class QuoteService(
    private val quoteRepository: QuoteRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val mailSenderService: MailSenderService,
    private val scopeResolver: ScopeResolver,
    private val leadProductRepository: LeadProductRepository,
) {
    fun filter(
        leadId: UUID?,
        status: QuoteStatus?,
        pageable: Pageable,
    ): PageResponse<QuoteResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(quoteRepository.filter(leadId, status, pageable)) { it.toResponse() }
        } else {
            PageResponse.of(quoteRepository.filterScoped(leadId, status, scopeIds, pageable)) { it.toResponse() }
        }
    }

    fun findById(id: UUID): QuoteResponse =
        (quoteRepository.findActiveById(id) ?: throw NoSuchElementException("Quote not found: $id")).toResponse()

    fun getLeadProducts(leadId: UUID): List<QuoteLeadProductResponse> =
        leadProductRepository.findAllWithProductInfoByLeadId(leadId).map { row ->
            QuoteLeadProductResponse(
                productId = row.productId,
                name = row.name,
                sku = row.sku,
                quantity = row.quantity,
                price = row.unitPrice ?: row.productPrice,
            )
        }

    @Transactional
    fun create(
        leadId: UUID,
        title: String,
        userId: UUID?,
        personId: UUID?,
        discount: BigDecimal,
        tax: BigDecimal,
        adjustment: BigDecimal,
        terms: String?,
        expiredAt: LocalDate?,
        billingAddress: Map<String, Any?>?,
        shippingAddress: Map<String, Any?>?,
        items: List<QuoteItemRequest>,
    ): QuoteResponse {
        val quote =
            quoteRepository.save(
                Quote().apply {
                    this.leadId = leadId
                    this.title = title
                    this.userId = userId
                    this.personId = personId
                    this.discount = discount
                    this.tax = tax
                    this.adjustment = adjustment
                    this.terms = terms
                    this.expiredAt = expiredAt
                    this.billingAddress = billingAddress
                    this.shippingAddress = shippingAddress
                },
            )
        items.forEach { req -> quote.items.add(buildItem(quote, req)) }
        val saved = quoteRepository.save(quote)
        syncLeadProducts(saved)
        eventPublisher.publishEvent(DomainEvent("quote.created", "Quote", saved.id!!, mapOf("leadId" to leadId)))
        return saved.toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        title: String,
        userId: UUID?,
        personId: UUID?,
        discount: BigDecimal,
        tax: BigDecimal,
        adjustment: BigDecimal,
        terms: String?,
        expiredAt: LocalDate?,
        billingAddress: Map<String, Any?>?,
        shippingAddress: Map<String, Any?>?,
        items: List<QuoteItemRequest>,
    ): QuoteResponse {
        val quote = requireQuote(id)
        quote.title = title
        quote.userId = userId
        quote.personId = personId
        quote.discount = discount
        quote.tax = tax
        quote.adjustment = adjustment
        quote.terms = terms
        quote.expiredAt = expiredAt
        quote.billingAddress = billingAddress
        quote.shippingAddress = shippingAddress
        quote.items.clear()
        items.forEach { req -> quote.items.add(buildItem(quote, req)) }
        val saved = quoteRepository.save(quote)
        syncLeadProducts(saved)
        return saved.toResponse()
    }

    /** Build a fresh [QuoteItem] from a request, owned by `quote`. Shared by create/update. */
    private fun buildItem(
        quote: Quote,
        req: QuoteItemRequest,
    ): QuoteItem =
        QuoteItem().apply {
            this.quote = quote
            this.quoteId = quote.id!!
            this.productId = req.productId
            this.quantity = req.quantity
            this.unitPrice = req.unitPrice
            this.discount = req.discount
        }

    /**
     * P3.4 / `02 § 2.3`: mirror quote items onto `lead_products` for the linked
     * lead. Quote-side deletes do **not** cascade to `lead_products` — a product
     * may be referenced from multiple quotes, and Krayin's behaviour is to leave
     * the lead-level association intact.
     */
    private fun syncLeadProducts(quote: Quote) {
        quote.items.forEach { item ->
            val productId = item.productId ?: return@forEach
            val existing = leadProductRepository.findByLeadIdAndProductId(quote.leadId, productId)
            if (existing == null) {
                leadProductRepository.save(
                    LeadProduct().apply {
                        this.leadId = quote.leadId
                        this.productId = productId
                        this.quantity = item.quantity
                        this.unitPrice = item.unitPrice
                    },
                )
            } else {
                existing.quantity = item.quantity
                existing.unitPrice = item.unitPrice
                leadProductRepository.save(existing)
            }
        }
    }

    /** P3.4 — `?expiredOnly=true` filter. */
    fun listExpired(pageable: Pageable): PageResponse<QuoteResponse> {
        val today = LocalDate.now()
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(quoteRepository.findExpired(today, pageable)) { it.toResponse() }
        } else {
            PageResponse.of(quoteRepository.findExpiredScoped(today, scopeIds, pageable)) { it.toResponse() }
        }
    }

    /** P3.4 — `/api/quotes/search?q=…`. */
    fun search(
        q: String,
        pageable: Pageable,
    ): PageResponse<QuoteResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(quoteRepository.search(q, pageable)) { it.toResponse() }
        } else {
            PageResponse.of(quoteRepository.searchScoped(q, scopeIds, pageable)) { it.toResponse() }
        }
    }

    @Transactional
    fun updateStatus(
        id: UUID,
        status: QuoteStatus,
    ): QuoteResponse {
        val quote = requireQuote(id)
        quote.status = status
        return quoteRepository.save(quote).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val quote = requireQuote(id)
        quote.deletedAt = Instant.now()
        quoteRepository.save(quote)
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            quoteRepository.findActiveById(id)?.let { quote ->
                quote.deletedAt = Instant.now()
                quoteRepository.save(quote)
            }
        }
    }

    fun sendMail(
        id: UUID,
        to: String,
        subject: String?,
        message: String?,
    ): SendQuoteMailResponse {
        val quote = requireQuote(id)
        val emailSubject = subject ?: "Quote: ${quote.title}"
        val quoteResponse = quote.toResponse()
        val body =
            buildString {
                appendLine("<h2>Quote: ${quote.title}</h2>")
                appendLine("<p>Grand Total: ${quoteResponse.grandTotal}</p>")
                if (!message.isNullOrBlank()) appendLine("<p>$message</p>")
            }
        mailSenderService.sendHtmlEmail(to, emailSubject, body)
        return SendQuoteMailResponse(sent = true, message = "Quote sent to $to")
    }

    @Transactional
    fun duplicate(id: UUID): QuoteResponse {
        val source = requireQuote(id)
        val copy =
            quoteRepository.save(
                Quote().apply {
                    this.leadId = source.leadId
                    this.userId = source.userId
                    this.title = "Copy of ${source.title}"
                    this.discount = source.discount
                    this.tax = source.tax
                    this.adjustment = source.adjustment
                    this.terms = source.terms
                    this.expiredAt = source.expiredAt
                    this.personId = source.personId
                    this.billingAddress = source.billingAddress
                    this.shippingAddress = source.shippingAddress
                },
            )
        copy.items.addAll(
            source.items.map { item ->
                QuoteItem().apply {
                    this.quote = copy
                    this.quoteId = copy.id!!
                    this.productId = item.productId
                    this.quantity = item.quantity
                    this.unitPrice = item.unitPrice
                    this.discount = item.discount
                }
            },
        )
        return quoteRepository.save(copy).toResponse()
    }

    private fun requireQuote(id: UUID): Quote =
        quoteRepository.findActiveById(id) ?: throw NoSuchElementException("Quote not found: $id")
}

fun Quote.toResponse(): QuoteResponse {
    val itemResponses = items.map { it.toResponse() }
    val subTotal = itemResponses.sumOf { it.lineTotal }
    val afterDiscount =
        subTotal.multiply(
            BigDecimal.ONE.subtract(discount.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)),
        )
    val taxAmount = afterDiscount.multiply(tax.divide(BigDecimal(100), 10, RoundingMode.HALF_UP))
    val grandTotal = afterDiscount.add(taxAmount).add(adjustment).setScale(2, RoundingMode.HALF_UP)
    return QuoteResponse(
        id = id!!,
        leadId = leadId,
        userId = userId,
        personId = personId,
        title = title,
        status = status.value,
        discount = discount,
        tax = tax,
        adjustment = adjustment,
        terms = terms,
        expiredAt = expiredAt,
        billingAddress = billingAddress,
        shippingAddress = shippingAddress,
        items = itemResponses,
        subTotal = subTotal.setScale(2, RoundingMode.HALF_UP),
        grandTotal = grandTotal,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

/**
 * Single source of truth for a quote line's pre-tax total: `unitPrice * quantity`
 * less a percentage discount. The discount division carries an explicit scale +
 * [RoundingMode]; an unscoped `divide` throws [ArithmeticException] on a
 * non-terminating quotient.
 */
internal fun quoteLineTotal(
    unitPrice: BigDecimal,
    quantity: Int,
    discountPercent: BigDecimal,
): BigDecimal =
    unitPrice
        .multiply(BigDecimal(quantity))
        .multiply(BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)))

fun QuoteItem.toResponse() =
    QuoteItemResponse(
        id = id!!,
        productId = productId,
        quantity = quantity,
        unitPrice = unitPrice,
        discount = discount,
        lineTotal = quoteLineTotal(unitPrice, quantity, discount).setScale(2, RoundingMode.HALF_UP),
    )
