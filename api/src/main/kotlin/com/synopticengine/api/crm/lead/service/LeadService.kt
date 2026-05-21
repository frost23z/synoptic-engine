package com.synopticengine.api.crm.lead.service

import com.synopticengine.api.crm.contact.domain.ContactEntry
import com.synopticengine.api.crm.contact.domain.Organization
import com.synopticengine.api.crm.contact.domain.Person
import com.synopticengine.api.crm.contact.repo.OrganizationRepository
import com.synopticengine.api.crm.contact.repo.PersonRepository
import com.synopticengine.api.crm.email.repo.EmailRepository
import com.synopticengine.api.crm.email.service.toResponse
import com.synopticengine.api.crm.email.web.EmailResponse
import com.synopticengine.api.crm.lead.domain.Lead
import com.synopticengine.api.crm.lead.domain.LeadProduct
import com.synopticengine.api.crm.lead.domain.LeadStatus
import com.synopticengine.api.crm.lead.repo.LeadProductRepository
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.lead.repo.PipelineRepository
import com.synopticengine.api.crm.lead.repo.StageRepository
import com.synopticengine.api.crm.lead.web.ConvertLeadResponse
import com.synopticengine.api.crm.lead.web.KanbanStageGroup
import com.synopticengine.api.crm.lead.web.LeadProductResponse
import com.synopticengine.api.crm.lead.web.LeadResponse
import com.synopticengine.api.crm.scoping.ScopeResolver
import com.synopticengine.api.crm.tag.repo.TagRepository
import com.synopticengine.api.crm.tag.service.toResponse
import com.synopticengine.api.shared.DomainEvent
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class LeadService(
    private val leadRepository: LeadRepository,
    private val stageRepository: StageRepository,
    private val pipelineRepository: PipelineRepository,
    private val personRepository: PersonRepository,
    private val organizationRepository: OrganizationRepository,
    private val tagRepository: TagRepository,
    private val emailRepository: EmailRepository,
    private val leadProductRepository: LeadProductRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val scopeResolver: ScopeResolver,
    private val objectMapper: ObjectMapper,
) {
    fun findAll(pageable: Pageable): PageResponse<LeadResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(leadRepository.findAllByDeletedAtIsNull(pageable)) { it.toResponse() }
        } else {
            PageResponse.of(leadRepository.findAllScoped(scopeIds, pageable)) { it.toResponse() }
        }
    }

    fun findById(id: UUID): LeadResponse =
        (leadRepository.findActiveById(id) ?: throw NoSuchElementException("Lead not found: $id")).toResponse()

    fun search(
        q: String,
        pageable: Pageable,
    ): PageResponse<LeadResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(leadRepository.search(q, pageable)) { it.toResponse() }
        } else {
            PageResponse.of(leadRepository.searchScoped(q, scopeIds, pageable)) { it.toResponse() }
        }
    }

    fun filter(
        pipelineId: UUID,
        stageId: UUID?,
        status: LeadStatus?,
        userId: UUID?,
        pageable: Pageable,
    ): PageResponse<LeadResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(leadRepository.filter(pipelineId, stageId, status, userId, pageable)) { it.toResponse() }
        } else {
            PageResponse.of(leadRepository.filterScoped(pipelineId, stageId, status, userId, scopeIds, pageable)) {
                it.toResponse()
            }
        }
    }

    fun kanban(pipelineId: UUID): List<KanbanStageGroup> {
        val stages = stageRepository.findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(pipelineId)
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        // Push the scope into the query so a user with INDIVIDUAL/GROUP view doesn't pull
        // every lead in the pipeline over the wire and discard most of them in memory.
        val leads =
            if (scopeIds == null) {
                leadRepository.findAllByPipelineIdAndDeletedAtIsNull(pipelineId)
            } else {
                leadRepository.findAllByPipelineIdScopedAndDeletedAtIsNull(pipelineId, scopeIds)
            }
        val leadsByStage = leads.groupBy { it.stageId }
        return stages.map { stage ->
            val stageLeads = leadsByStage[stage.id] ?: emptyList()
            KanbanStageGroup(
                stage = stage.toResponse(),
                leads = stageLeads.map { it.toResponse() },
                totalAmount = stageLeads.sumOf { it.amount ?: BigDecimal.ZERO },
            )
        }
    }

    fun findRottenLeads(pipelineId: UUID?): List<LeadResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        val leads =
            if (scopeIds == null) {
                leadRepository.findOpenForRotten(pipelineId)
            } else {
                leadRepository.findOpenForRottenScoped(pipelineId, scopeIds)
            }
        if (leads.isEmpty()) return emptyList()
        val pipelinesById = pipelineRepository.findAllById(leads.map { it.pipelineId }.toSet()).associateBy { it.id!! }
        val now = Instant.now()
        return leads
            .filter { lead ->
                val rottenDays = pipelinesById[lead.pipelineId]?.rottenDays ?: 30
                lead.stageUpdatedAt.isBefore(now.minusSeconds(rottenDays.toLong() * 86_400))
            }.map { it.toResponse() }
    }

    @Transactional
    fun create(
        title: String,
        description: String?,
        amount: BigDecimal?,
        expectedCloseDate: LocalDate?,
        pipelineId: UUID,
        stageId: UUID,
        personId: UUID?,
        organizationId: UUID?,
        leadSourceId: UUID?,
        leadTypeId: UUID?,
        userId: UUID?,
    ): LeadResponse {
        val lead =
            leadRepository.save(
                Lead().apply {
                    this.title = title
                    this.description = description
                    this.amount = amount
                    this.expectedCloseDate = expectedCloseDate
                    this.pipelineId = pipelineId
                    this.stageId = stageId
                    this.personId = personId
                    this.organizationId = organizationId
                    this.leadSourceId = leadSourceId
                    this.leadTypeId = leadTypeId
                    this.userId = userId
                    this.stageUpdatedAt = Instant.now()
                },
            )
        eventPublisher.publishEvent(
            DomainEvent("lead.created", "Lead", lead.id!!, mapOf("status" to lead.status.value, "stageId" to stageId)),
        )
        return lead.toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        title: String,
        description: String?,
        amount: BigDecimal?,
        expectedCloseDate: LocalDate?,
        status: LeadStatus?,
        lostReason: String?,
        pipelineId: UUID?,
        stageId: UUID?,
        personId: UUID?,
        organizationId: UUID?,
        leadSourceId: UUID?,
        leadTypeId: UUID?,
        userId: UUID?,
    ): LeadResponse {
        val lead = requireLead(id)
        val previousStageId = lead.stageId
        lead.title = title
        lead.description = description
        lead.amount = amount
        lead.expectedCloseDate = expectedCloseDate
        if (status != null) lead.status = status
        if (lostReason != null) lead.lostReason = lostReason
        if (pipelineId != null) lead.pipelineId = pipelineId
        if (stageId != null && stageId != lead.stageId) {
            lead.stageUpdatedAt = Instant.now()
            lead.stageId = stageId
        }
        lead.personId = personId
        lead.organizationId = organizationId
        lead.leadSourceId = leadSourceId
        lead.leadTypeId = leadTypeId
        lead.userId = userId
        val saved = leadRepository.save(lead)
        eventPublisher.publishEvent(
            DomainEvent("lead.updated", "Lead", saved.id!!, mapOf("status" to saved.status.value)),
        )
        if (saved.stageId != previousStageId) {
            publishLeadStageChanged(saved.id!!, saved.stageId, saved.status.value)
        }
        return saved.toResponse()
    }

    @Transactional
    fun moveStage(
        id: UUID,
        stageId: UUID,
        status: LeadStatus?,
        lostReason: String?,
    ): LeadResponse {
        val lead = requireLead(id)
        val stage =
            stageRepository.findByIdAndDeletedAtIsNull(stageId)
                ?: throw NoSuchElementException("Stage not found: $stageId")
        lead.stageId = stageId
        lead.pipelineId = stage.pipeline.id!!
        lead.stageUpdatedAt = Instant.now()
        if (status != null) {
            lead.status = status
            if (status != LeadStatus.OPEN) lead.closedAt = Instant.now()
        } else if (stage.code == "won") {
            lead.status = LeadStatus.WON
            lead.closedAt = Instant.now()
        } else if (stage.code == "lost") {
            lead.status = LeadStatus.LOST
            lead.closedAt = Instant.now()
            if (lostReason != null) lead.lostReason = lostReason
        }
        val saved = leadRepository.save(lead)
        publishLeadStageChanged(saved.id!!, stageId, saved.status.value)
        return saved.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val lead = requireLead(id)
        lead.deletedAt = Instant.now()
        leadRepository.save(lead)
    }

    @Transactional
    fun massUpdate(
        ids: List<UUID>,
        userId: UUID?,
        stageId: UUID?,
        status: LeadStatus?,
    ) {
        ids.forEach { id ->
            leadRepository.findActiveById(id)?.let { lead ->
                if (userId != null) lead.userId = userId
                if (stageId != null) lead.stageId = stageId
                if (status != null) lead.status = status
                leadRepository.save(lead)
            }
        }
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            leadRepository.findActiveById(id)?.let { lead ->
                lead.deletedAt = Instant.now()
                leadRepository.save(lead)
            }
        }
    }

    @Transactional
    fun attachTag(
        leadId: UUID,
        tagId: UUID,
    ): LeadResponse {
        val lead = requireLead(leadId)
        val tag = tagRepository.findActiveById(tagId) ?: throw NoSuchElementException("Tag not found: $tagId")
        lead.tags.add(tag)
        return leadRepository.save(lead).toResponse()
    }

    @Transactional
    fun detachTag(
        leadId: UUID,
        tagId: UUID,
    ): LeadResponse {
        val lead = requireLead(leadId)
        lead.tags.removeIf { it.id == tagId }
        return leadRepository.save(lead).toResponse()
    }

    fun findEmails(leadId: UUID): List<EmailResponse> {
        val lead = requireLead(leadId)
        return lead.emails.map { it.toResponse() }
    }

    @Transactional
    fun attachEmail(
        leadId: UUID,
        emailId: UUID,
    ): LeadResponse {
        val lead = requireLead(leadId)
        val email =
            emailRepository
                .findById(
                    emailId,
                ).orElseThrow { NoSuchElementException("Email not found: $emailId") }
        lead.emails.add(email)
        return leadRepository.save(lead).toResponse()
    }

    @Transactional
    fun detachEmail(
        leadId: UUID,
        emailId: UUID,
    ): LeadResponse {
        val lead = requireLead(leadId)
        lead.emails.removeIf { it.id == emailId }
        return leadRepository.save(lead).toResponse()
    }

    fun listProducts(leadId: UUID): List<LeadProductResponse> {
        requireLead(leadId)
        return leadProductRepository.findAllByLeadId(leadId).map { it.toResponse() }
    }

    @Transactional
    fun addProduct(
        leadId: UUID,
        productId: UUID,
        quantity: Int,
        unitPrice: BigDecimal?,
    ): LeadProductResponse {
        requireLead(leadId)
        val existing = leadProductRepository.findByLeadIdAndProductId(leadId, productId)
        if (existing != null) {
            existing.quantity = quantity
            if (unitPrice != null) existing.unitPrice = unitPrice
            return leadProductRepository.save(existing).toResponse()
        }
        return leadProductRepository
            .save(
                LeadProduct().apply {
                    this.leadId = leadId
                    this.productId = productId
                    this.quantity = quantity
                    this.unitPrice = unitPrice
                },
            ).toResponse()
    }

    @Transactional
    fun removeProduct(
        leadId: UUID,
        productId: UUID,
    ) {
        requireLead(leadId)
        leadProductRepository.deleteByLeadIdAndProductId(leadId, productId)
    }

    @Transactional
    fun convert(
        id: UUID,
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        jobTitle: String?,
        organizationId: UUID?,
        organizationName: String?,
        closeAsWon: Boolean,
    ): ConvertLeadResponse {
        val lead = requireLead(id)
        val organization =
            when {
                organizationId != null -> {
                    organizationRepository.findActiveById(organizationId)
                        ?: throw NoSuchElementException("Organization not found: $organizationId")
                }

                !organizationName.isNullOrBlank() -> {
                    organizationRepository.save(
                        Organization().apply {
                            name = organizationName
                        },
                    )
                }

                else -> {
                    null
                }
            }
        val person =
            personRepository.save(
                Person().apply {
                    this.firstName = firstName
                    this.lastName = lastName
                    this.organizationId = organization?.id
                    this.email = email
                    this.phone = phone
                    this.emails =
                        objectMapper.writeValueAsString(
                            email?.takeIf { it.isNotBlank() }?.let { listOf(ContactEntry(it, "primary")) }
                                ?: emptyList<ContactEntry>(),
                        )
                    this.contactNumbers =
                        objectMapper.writeValueAsString(
                            phone?.takeIf { it.isNotBlank() }?.let { listOf(ContactEntry(it, "primary")) }
                                ?: emptyList<ContactEntry>(),
                        )
                    this.jobTitle = jobTitle
                },
            )
        lead.personId = person.id
        if (organization != null) lead.organizationId = organization.id
        if (closeAsWon) {
            lead.status = LeadStatus.WON
            lead.closedAt = Instant.now()
        }
        leadRepository.save(lead)
        eventPublisher.publishEvent(DomainEvent("lead.converted", "Lead", lead.id!!, mapOf("personId" to person.id)))
        return ConvertLeadResponse(
            leadId = lead.id!!,
            personId = person.id!!,
            organizationId = organization?.id,
            status = lead.status.value,
        )
    }

    private fun requireLead(id: UUID): Lead =
        leadRepository.findActiveById(id) ?: throw NoSuchElementException("Lead not found: $id")

    private fun publishLeadStageChanged(
        leadId: UUID,
        stageId: UUID,
        status: String,
    ) {
        val payload =
            mapOf(
                "stageId" to stageId,
                "status" to status,
            )
        // Keep legacy dot form and Krayin-style underscore form for parity.
        eventPublisher.publishEvent(DomainEvent("lead.stage.changed", "Lead", leadId, payload))
        eventPublisher.publishEvent(DomainEvent("lead.stage_changed", "Lead", leadId, payload))
    }
}

fun LeadProduct.toResponse() =
    LeadProductResponse(
        id = id!!,
        leadId = leadId,
        productId = productId,
        quantity = quantity,
        unitPrice = unitPrice,
    )

fun Lead.toResponse() =
    LeadResponse(
        id = id!!,
        title = title,
        description = description,
        amount = amount,
        expectedCloseDate = expectedCloseDate,
        status = status.value,
        lostReason = lostReason,
        closedAt = closedAt,
        pipelineId = pipelineId,
        stageId = stageId,
        personId = personId,
        organizationId = organizationId,
        leadSourceId = leadSourceId,
        leadTypeId = leadTypeId,
        userId = userId,
        tags = tags.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
