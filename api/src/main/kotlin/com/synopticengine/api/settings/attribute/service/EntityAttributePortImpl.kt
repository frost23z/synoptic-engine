package com.synopticengine.api.settings.attribute.service

import com.synopticengine.api.shared.attribute.EntityAttributePort
import com.synopticengine.api.shared.attribute.EntityAttributeValueSummary
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class EntityAttributePortImpl(
    private val attributeService: AttributeService,
) : EntityAttributePort {
    override fun setEntityAttributeValue(
        attributeId: UUID,
        entityId: UUID,
        entityType: String,
        value: String?,
    ): EntityAttributeValueSummary {
        val result = attributeService.setValue(attributeId, entityId, entityType, value)
        return EntityAttributeValueSummary(
            id = result.id,
            attributeId = result.attributeId,
            entityId = result.entityId,
            entityType = result.entityType,
            value = result.value,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt,
        )
    }
}
