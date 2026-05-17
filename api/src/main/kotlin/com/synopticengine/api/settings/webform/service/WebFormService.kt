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
    fun findAll(): List<WebFormResponse> =
        webFormRepository.findAll().map { form ->
            (webFormRepository.findByIdWithFields(form.id!!) ?: form).toResponse()
        }

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
        fields: List<WebFormFieldRequest>,
    ): WebFormResponse {
        val form =
            webFormRepository.save(
                WebForm().apply {
                    this.title = title
                    this.description = description
                    this.isActive =
                        isActive
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
        fields: List<WebFormFieldRequest>,
    ): WebFormResponse {
        val form = webFormRepository.findByIdWithFields(id) ?: throw NoSuchElementException("Web form not found: $id")
        form.title = title
        form.description = description
        form.isActive = isActive
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
        if (!webFormRepository.existsById(id)) throw NoSuchElementException("Web form not found: $id")
        webFormRepository.deleteById(id)
    }
}

fun WebForm.toResponse() =
    WebFormResponse(
        id = id!!,
        title = title,
        description = description,
        isActive = isActive,
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
