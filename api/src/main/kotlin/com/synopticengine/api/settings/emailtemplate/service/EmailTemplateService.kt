package com.synopticengine.api.settings.emailtemplate.service

import com.synopticengine.api.settings.emailtemplate.domain.EmailTemplate
import com.synopticengine.api.settings.emailtemplate.repo.EmailTemplateRepository
import com.synopticengine.api.settings.emailtemplate.web.EmailTemplateResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class EmailTemplateService(
    private val emailTemplateRepository: EmailTemplateRepository,
) {
    fun findAll(): List<EmailTemplateResponse> = emailTemplateRepository.findAll().map { it.toResponse() }

    fun findById(id: UUID): EmailTemplateResponse =
        emailTemplateRepository
            .findById(id)
            .orElseThrow {
                NoSuchElementException("Email template not found: $id")
            }.toResponse()

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
                    this.content = content
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
        val template =
            emailTemplateRepository.findById(id).orElseThrow {
                NoSuchElementException("Email template not found: $id")
            }
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
        template.content = content
        return emailTemplateRepository.save(template).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val template =
            emailTemplateRepository.findById(id).orElseThrow {
                NoSuchElementException("Email template not found: $id")
            }
        if (template.isPredefined) throw IllegalStateException("Cannot delete predefined templates")
        emailTemplateRepository.deleteById(id)
    }
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
