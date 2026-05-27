package com.synopticengine.api.settings.emailtemplate.service

import com.synopticengine.api.settings.emailtemplate.domain.EmailTemplate
import com.synopticengine.api.settings.emailtemplate.repo.EmailTemplateRepository
import com.synopticengine.api.settings.emailtemplate.web.EmailTemplateResponse
import com.synopticengine.api.shared.email.HtmlSanitizer
import com.synopticengine.api.shared.email.interpolateTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class EmailTemplateService(
    private val emailTemplateRepository: EmailTemplateRepository,
) {
    fun findAll(): List<EmailTemplateResponse> = emailTemplateRepository.findAll().map { it.toResponse() }

    fun findById(id: UUID): EmailTemplateResponse = requireTemplate(id).toResponse()

    @Transactional
    fun create(
        name: String,
        subject: String,
        content: String,
    ): EmailTemplateResponse {
        if (emailTemplateRepository.existsByName(
                name,
            )
        ) {
            throw IllegalStateException("Template name already in use: $name")
        }
        return emailTemplateRepository
            .save(
                EmailTemplate().apply {
                    this.name = name
                    this.subject = subject
                    this.content = HtmlSanitizer.sanitize(content)
                },
            ).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
        subject: String,
        content: String,
    ): EmailTemplateResponse {
        val template = requireTemplate(id)
        if (template.isPredefined) throw IllegalStateException("Cannot modify predefined templates")
        if (emailTemplateRepository.existsByNameAndIdNot(
                name,
                id,
            )
        ) {
            throw IllegalStateException("Template name already in use: $name")
        }
        template.name = name
        template.subject = subject
        template.content = HtmlSanitizer.sanitize(content)
        return emailTemplateRepository.save(template).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val template = requireTemplate(id)
        if (template.isPredefined) throw IllegalStateException("Cannot delete predefined templates")
        emailTemplateRepository.delete(template)
    }

    fun render(
        id: UUID,
        context: Map<String, String>,
    ): EmailTemplateResponse {
        val template = requireTemplate(id)
        return EmailTemplateResponse(
            id = template.id!!,
            name = template.name,
            subject = interpolateTemplate(template.subject, context),
            content = interpolateTemplate(template.content, context),
            isPredefined = template.isPredefined,
            createdAt = template.createdAt,
            updatedAt = template.updatedAt,
        )
    }

    // Tenant-aware load. See EmailService.requireEmail for the IDOR rationale.
    private fun requireTemplate(id: UUID): EmailTemplate =
        emailTemplateRepository.findActiveById(id)
            ?: throw NoSuchElementException("Email template not found: $id")
}

fun EmailTemplate.toResponse() =
    EmailTemplateResponse(
        id = id!!,
        name = name,
        subject = subject,
        content = content,
        isPredefined = isPredefined,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
