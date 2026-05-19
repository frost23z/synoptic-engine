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
    val content: String,
)

data class WebhookSummary(
    val id: UUID,
    val name: String,
    val payloadUrl: String,
    val isActive: Boolean,
)

data class WebFormFieldSummary(
    val attributeId: UUID,
    val sortOrder: Int,
    val isRequired: Boolean,
)

data class WebFormSummary(
    val id: UUID,
    val tenantId: UUID,
    val title: String,
    val isActive: Boolean,
    val isDeleted: Boolean,
    val fields: List<WebFormFieldSummary>,
)

interface SettingsApi {
    fun findAttributesByEntityType(entityType: String): List<AttributeSummary>

    fun findEmailTemplateById(id: UUID): EmailTemplateSummary?

    fun findWebhookById(id: UUID): WebhookSummary?

    /** Cross-tenant lookup for the public submit endpoint. */
    fun findWebFormById(id: UUID): WebFormSummary?
}
