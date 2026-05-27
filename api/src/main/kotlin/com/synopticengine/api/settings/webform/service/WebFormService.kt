package com.synopticengine.api.settings.webform.service

import com.synopticengine.api.settings.webform.domain.WebForm
import com.synopticengine.api.settings.webform.domain.WebFormAttribute
import com.synopticengine.api.settings.webform.repo.WebFormRepository
import com.synopticengine.api.settings.webform.web.WebFormFieldRequest
import com.synopticengine.api.settings.webform.web.WebFormFieldResponse
import com.synopticengine.api.settings.webform.web.WebFormResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class WebFormService(
    private val webFormRepository: WebFormRepository,
) {
    // T5.1 — previously called findAll() then findByIdWithFields() per form (N+1).
    // Now a single DISTINCT LEFT JOIN FETCH query loads all forms and their fields.
    fun findAll(): List<WebFormResponse> =
        webFormRepository.findAllWithFields().map { it.toResponse() }

    fun findById(id: UUID): WebFormResponse =
        (
            webFormRepository.findByIdWithFields(
                id,
            ) ?: throw NoSuchElementException("Web form not found: $id")
        ).toResponse()

    fun findPublicById(id: UUID): WebFormResponse {
        val form = webFormRepository.findByIdWithFields(id) ?: throw NoSuchElementException("Web form not found: $id")
        if (!form.isActive) throw NoSuchElementException("Web form not found: $id")
        return form.toResponse()
    }

    @Transactional
    fun create(
        title: String,
        description: String?,
        isActive: Boolean,
        createLead: Boolean,
        backgroundColor: String?,
        submitSuccessAction: String,
        submitSuccessMessage: String?,
        submitSuccessUrl: String?,
        captchaEnabled: Boolean,
        fields: List<WebFormFieldRequest>,
    ): WebFormResponse {
        val form =
            webFormRepository.save(
                WebForm().apply {
                    this.title = title
                    this.description = description
                    this.isActive =
                        isActive
                    this.createLead = createLead
                    this.backgroundColor = backgroundColor
                    this.submitSuccessAction = submitSuccessAction
                    this.submitSuccessMessage = submitSuccessMessage
                    this.submitSuccessUrl = submitSuccessUrl
                    this.captchaEnabled = captchaEnabled
                },
            )
        fields.forEach { req ->
            form.fields.add(
                WebFormAttribute().apply {
                    this.webForm = form
                    this.webFormId = form.id!!
                    this.attributeId = req.attributeId
                    this.sortOrder = req.sortOrder
                    this.isRequired = req.isRequired
                },
            )
        }
        return webFormRepository.save(form).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        title: String,
        description: String?,
        isActive: Boolean,
        createLead: Boolean,
        backgroundColor: String?,
        submitSuccessAction: String,
        submitSuccessMessage: String?,
        submitSuccessUrl: String?,
        captchaEnabled: Boolean,
        fields: List<WebFormFieldRequest>,
    ): WebFormResponse {
        val form = webFormRepository.findByIdWithFields(id) ?: throw NoSuchElementException("Web form not found: $id")
        form.title = title
        form.description = description
        form.isActive = isActive
        form.createLead = createLead
        form.backgroundColor = backgroundColor
        form.submitSuccessAction = submitSuccessAction
        form.submitSuccessMessage = submitSuccessMessage
        form.submitSuccessUrl = submitSuccessUrl
        form.captchaEnabled = captchaEnabled
        form.fields.clear()
        fields.forEach { req ->
            form.fields.add(
                WebFormAttribute().apply {
                    this.webForm = form
                    this.webFormId = form.id!!
                    this.attributeId = req.attributeId
                    this.sortOrder = req.sortOrder
                    this.isRequired = req.isRequired
                },
            )
        }
        return webFormRepository.save(form).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        // Load via the tenant-aware JPQL finder so cross-tenant deletes 404.
        val form =
            webFormRepository.findByIdWithFields(id)
                ?: throw NoSuchElementException("Web form not found: $id")
        webFormRepository.delete(form)
    }
}

fun WebForm.toResponse() =
    WebFormResponse(
        id = id!!,
        title = title,
        description = description,
        isActive = isActive,
        createLead = createLead,
        backgroundColor = backgroundColor,
        submitSuccessAction = submitSuccessAction,
        submitSuccessMessage = submitSuccessMessage,
        submitSuccessUrl = submitSuccessUrl,
        captchaEnabled = captchaEnabled,
        fields = fields.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WebFormAttribute.toResponse() =
    WebFormFieldResponse(
        id = id!!,
        attributeId = attributeId,
        sortOrder = sortOrder,
        isRequired = isRequired,
    )
