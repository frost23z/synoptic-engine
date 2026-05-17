package com.synopticengine.api.crm.quote.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "quote_items")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class QuoteItem : BaseEntity() {
    @Column(name = "quote_id", insertable = false, updatable = false)
    var quoteId: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    lateinit var quote: Quote

    @Column
    var productId: UUID? = null

    @Column(nullable = false)
    var quantity: Int = 1

    @Column(precision = 15, scale = 2, nullable = false)
    var unitPrice: BigDecimal = BigDecimal.ZERO

    @Column(precision = 5, scale = 2, nullable = false)
    var discount: BigDecimal = BigDecimal.ZERO

    val lineTotal: BigDecimal
        get() =
            unitPrice
                .multiply(
                    BigDecimal(quantity),
                ).multiply(BigDecimal.ONE.subtract(discount.divide(BigDecimal(100))))
}
