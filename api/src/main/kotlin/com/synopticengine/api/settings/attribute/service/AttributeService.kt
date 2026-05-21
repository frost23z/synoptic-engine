package com.synopticengine.api.settings.attribute.service

import com.synopticengine.api.settings.attribute.domain.Attribute
import com.synopticengine.api.settings.attribute.domain.AttributeOption
import com.synopticengine.api.settings.attribute.domain.AttributeType
import com.synopticengine.api.settings.attribute.domain.AttributeValue
import com.synopticengine.api.settings.attribute.repo.AttributeOptionRepository
import com.synopticengine.api.settings.attribute.repo.AttributeRepository
import com.synopticengine.api.settings.attribute.repo.AttributeValueRepository
import com.synopticengine.api.settings.attribute.web.AttributeLookupItem
import com.synopticengine.api.settings.attribute.web.AttributeOptionResponse
import com.synopticengine.api.settings.attribute.web.AttributeResponse
import com.synopticengine.api.settings.attribute.web.AttributeValueResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AttributeService(
    private val attributeRepository: AttributeRepository,
    private val optionRepository: AttributeOptionRepository,
    private val valueRepository: AttributeValueRepository,
) {
    fun findAll(entityType: String?): List<AttributeResponse> =
        if (entityType != null) {
            attributeRepository.findAllByEntityType(entityType).map { attr ->
                attr.toResponse(optionRepository.findAllByAttributeId(attr.id!!))
            }
        } else {
            attributeRepository.findAll().map { attr ->
                attr.toResponse(optionRepository.findAllByAttributeId(attr.id!!))
            }
        }

    fun findById(id: UUID): AttributeResponse =
        (attributeRepository.findByIdWithOptions(id) ?: throw NoSuchElementException("Attribute not found: $id"))
            .toResponseWithLoadedOptions()

    fun getEntityValues(
        entityId: UUID,
        entityType: String,
    ): List<AttributeValueResponse> =
        valueRepository.findAllByEntityIdAndEntityType(entityId, entityType).map {
            it.toResponse()
        }

    @Transactional
    fun create(
        code: String,
        adminName: String,
        type: AttributeType,
        entityType: String,
        isUserDefined: Boolean,
        lookup: String?,
        sortOrder: Int,
    ): AttributeResponse {
        if (attributeRepository.existsByCodeAndEntityType(code, entityType)) {
            throw IllegalStateException("Attribute code '$code' already exists for entity type '$entityType'")
        }
        val attr =
            attributeRepository.save(
                Attribute().apply {
                    this.code = code
                    this.adminName = adminName
                    this.type = type
                    this.entityType = entityType
                    this.isUserDefined = isUserDefined
                    this.lookup = lookup
                    this.sortOrder = sortOrder
                },
            )
        return attr.toResponse(emptyList())
    }

    @Transactional
    fun update(
        id: UUID,
        adminName: String,
        type: AttributeType,
        lookup: String?,
        sortOrder: Int,
    ): AttributeResponse {
        val attr = requireAttr(id)
        attr.adminName = adminName
        attr.type = type
        attr.lookup = lookup
        attr.sortOrder = sortOrder
        attributeRepository.save(attr)
        return attr.toResponse(optionRepository.findAllByAttributeId(id))
    }

    @Transactional
    fun delete(id: UUID) {
        attributeRepository.delete(requireAttr(id))
    }

    @Transactional
    fun addOption(
        attributeId: UUID,
        adminName: String,
        sortOrder: Int,
    ): AttributeOptionResponse {
        val attr = requireAttr(attributeId)
        val option =
            optionRepository.save(
                AttributeOption().apply {
                    this.attribute = attr
                    this.attributeId = attr.id!!
                    this.adminName = adminName
                    this.sortOrder = sortOrder
                },
            )
        return option.toResponse()
    }

    @Transactional
    fun updateOption(
        attributeId: UUID,
        optionId: UUID,
        adminName: String,
        sortOrder: Int,
    ): AttributeOptionResponse {
        requireAttr(attributeId)
        val option =
            optionRepository.findActiveById(optionId)
                ?: throw NoSuchElementException("Option not found: $optionId")
        option.adminName = adminName
        option.sortOrder = sortOrder
        return optionRepository.save(option).toResponse()
    }

    @Transactional
    fun deleteOption(
        attributeId: UUID,
        optionId: UUID,
    ) {
        requireAttr(attributeId)
        val option =
            optionRepository.findActiveById(optionId)
                ?: throw NoSuchElementException("Option not found: $optionId")
        optionRepository.delete(option)
    }

    @Transactional
    fun setValue(
        attributeId: UUID,
        entityId: UUID,
        entityType: String,
        value: String?,
    ): AttributeValueResponse {
        requireAttr(attributeId)
        val existing = valueRepository.findByAttributeIdAndEntityId(attributeId, entityId)
        return if (existing != null) {
            existing.value = value
            valueRepository.save(existing).toResponse()
        } else {
            valueRepository
                .save(
                    AttributeValue().apply {
                        this.attributeId = attributeId
                        this.entityId = entityId
                        this.entityType = entityType
                        this.value = value
                    },
                ).toResponse()
        }
    }

    @Transactional
    fun massUpdate(
        ids: List<UUID>,
        adminName: String?,
        sortOrder: Int?,
    ) {
        ids.forEach { id ->
            attributeRepository.findActiveById(id)?.let { attr ->
                if (adminName != null) attr.adminName = adminName
                if (sortOrder != null) attr.sortOrder = sortOrder
                attributeRepository.save(attr)
            }
        }
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            val attr = attributeRepository.findActiveById(id) ?: return@forEach
            if (!attr.isUserDefined) {
                throw IllegalArgumentException("Cannot delete system attribute: ${attr.code}")
            }
            attributeRepository.delete(attr)
        }
    }

    fun lookup(lookupCode: String): List<AttributeLookupItem> {
        val attributes = attributeRepository.findAllByLookup(lookupCode)
        return attributes.flatMap { attr ->
            optionRepository.findAllByAttributeId(attr.id!!).map { opt ->
                AttributeLookupItem(id = opt.id.toString(), label = opt.adminName)
            }
        }
    }

    fun checkUniqueValidation(
        code: String,
        entityType: String,
        value: String,
        excludeId: UUID?,
    ): Boolean {
        val matchingValues = valueRepository.findAllByEntityTypeAndValue(entityType, value)
        val attr =
            attributeRepository.findAllByEntityType(entityType).find { it.code == code }
                ?: return true
        val filtered = matchingValues.filter { av -> av.attributeId == attr.id }
        return if (excludeId != null) {
            filtered.none { it.entityId != excludeId }
        } else {
            filtered.isEmpty()
        }
    }

    fun downloadCsv(): String {
        val sb = StringBuilder("id,code,admin_name,type,entity_type,sort_order,is_user_defined\n")
        attributeRepository.findAll().forEach { attr ->
            sb.append(
                "${attr.id},${attr.code},${attr.adminName},${attr.type},${attr.entityType},${attr.sortOrder},${attr.isUserDefined}\n",
            )
        }
        return sb.toString()
    }

    // Tenant-aware load. See EmailService.requireEmail for the IDOR rationale.
    private fun requireAttr(id: UUID): Attribute =
        attributeRepository.findActiveById(id) ?: throw NoSuchElementException("Attribute not found: $id")
}

fun Attribute.toResponse(opts: List<AttributeOption>): AttributeResponse =
    AttributeResponse(
        id = id!!,
        code = code,
        adminName = adminName,
        type = type.name,
        isUserDefined = isUserDefined,
        lookup = lookup,
        entityType = entityType,
        sortOrder = sortOrder,
        options = opts.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Attribute.toResponseWithLoadedOptions(): AttributeResponse = toResponse(options.toList())

fun AttributeOption.toResponse() =
    AttributeOptionResponse(
        id = id!!,
        attributeId = attributeId,
        adminName = adminName,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

fun AttributeValue.toResponse() =
    AttributeValueResponse(
        id = id!!,
        attributeId = attributeId,
        entityId = entityId,
        entityType = entityType,
        value = value,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
