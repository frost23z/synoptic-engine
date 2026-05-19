package com.synopticengine.api.sharing.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import jakarta.persistence.EntityListeners
import java.time.Instant
import java.util.UUID

/**
 * Materialized visibility index. Read on the hot path by RLS policies (via
 * `app_has_visibility(...)`) and by service-layer queries that need to know "is
 * this id visible to me as a consumer".
 *
 * Owned by triggers / the materialization worker. The application layer reads it
 * but should not insert rows directly outside of `ResourceVisibilityService`.
 */
@Entity
@Table(
    name = "resource_visibility",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_visibility",
            columnNames = ["consumer_tenant_id", "resource_type", "resource_id", "source", "source_id"],
        ),
    ],
)
@EntityListeners(AuditingEntityListener::class)
class ResourceVisibility {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

    @Column(name = "owner_tenant_id", nullable = false, columnDefinition = "uuid")
    var ownerTenantId: UUID = UUID(0, 0)

    @Column(name = "consumer_tenant_id", nullable = false, columnDefinition = "uuid")
    var consumerTenantId: UUID = UUID(0, 0)

    @Column(name = "resource_type", nullable = false, length = 50)
    var resourceType: String = ""

    @Column(name = "resource_id", nullable = false, columnDefinition = "uuid")
    var resourceId: UUID = UUID(0, 0)

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    var accessLevel: AccessLevel = AccessLevel.READ

    @Convert(converter = VisibilitySourceConverter::class)
    @Column(nullable = false, length = 20)
    var source: VisibilitySource = VisibilitySource.POLICY

    @Column(name = "source_id", nullable = false, columnDefinition = "uuid")
    var sourceId: UUID = UUID(0, 0)

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set
}

@Converter(autoApply = false)
class VisibilitySourceConverter : AttributeConverter<VisibilitySource, String> {
    override fun convertToDatabaseColumn(attr: VisibilitySource?): String? = attr?.literal

    override fun convertToEntityAttribute(dbData: String?): VisibilitySource? =
        dbData?.let { VisibilitySource.fromLiteral(it) }
}
