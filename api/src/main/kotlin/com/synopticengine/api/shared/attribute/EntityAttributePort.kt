package com.synopticengine.api.shared.attribute

import java.time.Instant
import java.util.UUID

data class EntityAttributeValueSummary(
    val id: UUID,
    val attributeId: UUID,
    val entityId: UUID,
    val entityType: String,
    val value: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

interface EntityAttributePort {
    fun setEntityAttributeValue(
        attributeId: UUID,
        entityId: UUID,
        entityType: String,
        value: String?,
    ): EntityAttributeValueSummary
}
