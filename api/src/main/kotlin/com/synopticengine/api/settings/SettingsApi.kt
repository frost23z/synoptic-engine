package com.synopticengine.api.settings

import java.util.UUID

data class AttributeSummary(
    val id: UUID,
    val code: String,
    val adminName: String,
    val type: String,
    val entityType: String,
    val sortOrder: Int,
)

data class EmailTemplateSummary(
    val id: UUID,
    val name: String,
    val subject: String,
)

interface SettingsApi {
    fun findAttributesByEntityType(entityType: String): List<AttributeSummary>

    fun findEmailTemplateById(id: UUID): EmailTemplateSummary?
}
