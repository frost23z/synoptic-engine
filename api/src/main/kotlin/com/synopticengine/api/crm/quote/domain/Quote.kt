package com.synopticengine.api.crm.quote.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "quotes")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Quote :
    AuditableEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var leadId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    @Column
    var userId: UUID? = null

    @Column(nullable = false)
    var title: String = ""

    @Convert(converter = QuoteStatusConverter::class)
    @Column(nullable = false)
    var status: QuoteStatus = QuoteStatus.DRAFT

    @Column(precision = 5, scale = 2, nullable = false)
    var discount: BigDecimal = BigDecimal.ZERO

    @Column(precision = 5, scale = 2, nullable = false)
    var tax: BigDecimal = BigDecimal.ZERO

    @Column(columnDefinition = "TEXT")
    var terms: String? = null

    @Column
    var expiredAt: LocalDate? = null

    @Column
    var personId: UUID? = null

    /** Billing address as JSON: `{ street, city, state, country, postcode }`. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var billingAddress: String? = null

    /** Shipping address as JSON: same shape as billingAddress. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var shippingAddress: String? = null

    @Column
    override var deletedAt: Instant? = null

    @OneToMany(mappedBy = "quote", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<QuoteItem> = mutableListOf()
}
