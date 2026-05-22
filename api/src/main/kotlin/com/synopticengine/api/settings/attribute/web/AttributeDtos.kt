package com.synopticengine.api.settings.attribute.web

import com.synopticengine.api.settings.attribute.domain.AttributeType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateAttributeRequest(
    @field:NotBlank(message = "Code is required")
    val code: String,
    @field:NotBlank(message = "Admin name is required")
    val adminName: String,
    @field:NotNull(message = "Type is required")
    val type: AttributeType,
    @field:NotBlank(message = "Entity type is required")
    val entityType: String,
    val isUserDefined: Boolean = true,
    val isRequired: Boolean = false,
    val isUnique: Boolean = false,
    val quickAdd: Boolean = false,
    val lookup: String? = null,
    val lookupType: String? = null,
    val validationRules: Map<String, Any?> = emptyMap(),
    val sortOrder: Int = 0,
)

data class UpdateAttributeRequest(
    @field:NotBlank(message = "Admin name is required")
    val adminName: String,
    @field:NotNull(message = "Type is required")
    val type: AttributeType,
    val isRequired: Boolean = false,
    val isUnique: Boolean = false,
    val quickAdd: Boolean = false,
    val lookup: String? = null,
    val lookupType: String? = null,
    val validationRules: Map<String, Any?> = emptyMap(),
    val sortOrder: Int = 0,
)

data class AttributeOptionRequest(
    @field:NotBlank(message = "Name is required")
    val adminName: String,
    val sortOrder: Int = 0,
)

data class SetAttributeValueRequest(
    val attributeId: UUID,
    val entityId: UUID,
    val entityType: String,
    val value: String?,
)

data class AttributeOptionResponse(
    val id: UUID,
    val attributeId: UUID,
    val adminName: String,
    val sortOrder: Int,
    val createdAt: Instant?,
)

data class AttributeResponse(
    val id: UUID,
    val code: String,
    val adminName: String,
    val type: String,
    val isUserDefined: Boolean,
    val isRequired: Boolean,
    val isUnique: Boolean,
    val quickAdd: Boolean,
    val lookup: String?,
    val lookupType: String?,
    val validationRules: Map<String, Any?>,
    val entityType: String,
    val sortOrder: Int,
    val options: List<AttributeOptionResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class AttributeValueResponse(
    val id: UUID,
    val attributeId: UUID,
    val entityId: UUID,
    val entityType: String,
    val value: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class MassUpdateAttributeRequest(
    val ids: List<UUID>,
    val adminName: String? = null,
    val sortOrder: Int? = null,
)

data class MassDestroyAttributeRequest(
    val ids: List<UUID>,
)

data class AttributeLookupItem(
    val id: String,
    val label: String,
)
