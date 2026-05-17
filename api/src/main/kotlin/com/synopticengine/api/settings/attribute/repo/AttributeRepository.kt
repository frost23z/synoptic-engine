package com.synopticengine.api.settings.attribute.repo

import com.synopticengine.api.settings.attribute.domain.Attribute
import com.synopticengine.api.settings.attribute.domain.AttributeOption
import com.synopticengine.api.settings.attribute.domain.AttributeValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface AttributeRepository : JpaRepository<Attribute, UUID> {
    fun findAllByEntityType(entityType: String): List<Attribute>

    fun findAllByLookup(lookup: String): List<Attribute>

    fun existsByCodeAndEntityType(
        code: String,
        entityType: String,
    ): Boolean

    fun existsByCodeAndEntityTypeAndIdNot(
        code: String,
        entityType: String,
        id: UUID,
    ): Boolean

    @Query("SELECT a FROM Attribute a LEFT JOIN FETCH a.options WHERE a.id = :id")
    fun findByIdWithOptions(id: UUID): Attribute?
}

interface AttributeOptionRepository : JpaRepository<AttributeOption, UUID> {
    fun findAllByAttributeId(attributeId: UUID): List<AttributeOption>
}

interface AttributeValueRepository : JpaRepository<AttributeValue, UUID> {
    fun findAllByEntityIdAndEntityType(
        entityId: UUID,
        entityType: String,
    ): List<AttributeValue>

    fun findByAttributeIdAndEntityId(
        attributeId: UUID,
        entityId: UUID,
    ): AttributeValue?

    fun findAllByEntityTypeAndValue(
        entityType: String,
        value: String,
    ): List<AttributeValue>
}
