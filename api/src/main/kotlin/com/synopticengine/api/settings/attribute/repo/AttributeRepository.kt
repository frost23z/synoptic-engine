package com.synopticengine.api.settings.attribute.repo

import com.synopticengine.api.settings.attribute.domain.Attribute
import com.synopticengine.api.settings.attribute.domain.AttributeOption
import com.synopticengine.api.settings.attribute.domain.AttributeValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Query("SELECT a FROM Attribute a LEFT JOIN FETCH a.options WHERE a.id = :id AND a.deletedAt IS NULL")
    fun findByIdWithOptions(id: UUID): Attribute?

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT a FROM Attribute a WHERE a.id = :id AND a.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): Attribute?
}

interface AttributeOptionRepository : JpaRepository<AttributeOption, UUID> {
    fun findAllByAttributeId(attributeId: UUID): List<AttributeOption>

    fun findAllByAttributeIdIn(attributeIds: Collection<UUID>): List<AttributeOption>

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT o FROM AttributeOption o WHERE o.id = :id")
    fun findActiveById(
        @Param("id") id: UUID,
    ): AttributeOption?
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

    fun findByAttributeIdAndEntityIdAndEntityType(
        attributeId: UUID,
        entityId: UUID,
        entityType: String,
    ): AttributeValue?

    fun findAllByEntityTypeAndValue(
        entityType: String,
        value: String,
    ): List<AttributeValue>
}
