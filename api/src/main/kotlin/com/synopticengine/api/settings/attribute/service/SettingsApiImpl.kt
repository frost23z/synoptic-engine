package com.synopticengine.api.settings.attribute.service

import com.synopticengine.api.settings.AttributeSummary
import com.synopticengine.api.settings.EmailTemplateSummary
import com.synopticengine.api.settings.SettingsApi
import com.synopticengine.api.settings.attribute.repo.AttributeRepository
import com.synopticengine.api.settings.emailtemplate.repo.EmailTemplateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SettingsApiImpl(
    private val attributeRepository: AttributeRepository,
    private val emailTemplateRepository: EmailTemplateRepository,
) : SettingsApi {
    override fun findAttributesByEntityType(entityType: String): List<AttributeSummary> =
        attributeRepository.findAllByEntityType(entityType).map {
            AttributeSummary(
                id = it.id!!,
                code = it.code,
                adminName = it.adminName,
                type = it.type.name,
                entityType = it.entityType,
                sortOrder = it.sortOrder,
            )
        }

    override fun findEmailTemplateById(id: UUID): EmailTemplateSummary? =
        emailTemplateRepository.findById(id).orElse(null)?.let {
            EmailTemplateSummary(id = it.id!!, name = it.name, subject = it.subject)
        }
}
