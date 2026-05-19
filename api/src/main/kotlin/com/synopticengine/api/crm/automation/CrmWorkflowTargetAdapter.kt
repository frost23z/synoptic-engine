package com.synopticengine.api.crm.automation

import com.synopticengine.api.crm.activity.domain.Activity
import com.synopticengine.api.crm.activity.domain.ActivityType
import com.synopticengine.api.crm.activity.repo.ActivityRepository
import com.synopticengine.api.crm.contact.domain.ContactEntry
import com.synopticengine.api.crm.contact.repo.PersonRepository
import com.synopticengine.api.crm.contact.service.PersonService
import com.synopticengine.api.crm.lead.domain.LeadStatus
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.lead.repo.PipelineRepository
import com.synopticengine.api.crm.lead.repo.StageRepository
import com.synopticengine.api.crm.lead.service.LeadService
import com.synopticengine.api.crm.tag.domain.Tag
import com.synopticengine.api.crm.tag.repo.TagRepository
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Phase 3 / P3.2 + P3.5 — CRM-side implementation of [WorkflowTargetPort].
 *
 * Lives in `crm.automation` so it can touch CRM repositories and services
 * directly; consumers in `shared.automation.actions` and `shared.webform`
 * go through the port interface to stay module-neutral.
 */
@Service
@Transactional
class CrmWorkflowTargetAdapter(
    private val leadRepository: LeadRepository,
    private val personRepository: PersonRepository,
    private val tagRepository: TagRepository,
    private val activityRepository: ActivityRepository,
    private val personService: PersonService,
    private val leadService: LeadService,
    private val pipelineRepository: PipelineRepository,
    private val stageRepository: StageRepository,
) : WorkflowTargetPort {
    private val log = LoggerFactory.getLogger(CrmWorkflowTargetAdapter::class.java)

    override fun updateLeadField(
        leadId: UUID,
        field: String,
        value: String?,
    ): UUID? {
        val lead = leadRepository.findActiveById(leadId) ?: return null
        when (field) {
            "title" -> lead.title = value.orEmpty()
            "description" -> lead.description = value
            "amount" -> lead.amount = value?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            "stageId" -> lead.stageId = UUID.fromString(value)
            "userId" -> lead.userId = value?.let(UUID::fromString)
            "status" -> lead.status = LeadStatus.fromValue(value ?: "open")
            "leadSourceId" -> lead.leadSourceId = value?.let(UUID::fromString)
            "leadTypeId" -> lead.leadTypeId = value?.let(UUID::fromString)
            else -> throw IllegalArgumentException("update_lead does not support field '$field'")
        }
        leadRepository.save(lead)
        return lead.id
    }

    override fun updatePersonField(
        personId: UUID,
        field: String,
        value: String?,
    ): UUID? {
        val person = personRepository.findActiveById(personId) ?: return null
        when (field) {
            "firstName" -> person.firstName = value.orEmpty()
            "lastName" -> person.lastName = value.orEmpty()
            "email" -> person.email = value
            "phone" -> person.phone = value
            "jobTitle" -> person.jobTitle = value
            "organizationId" -> person.organizationId = value?.let(UUID::fromString)
            else -> throw IllegalArgumentException("update_person does not support field '$field'")
        }
        personRepository.save(person)
        return person.id
    }

    override fun ensureLeadTag(
        leadId: UUID,
        tagId: UUID?,
        tagName: String?,
    ): UUID? {
        val lead = leadRepository.findActiveById(leadId) ?: return null
        val tag = resolveTag(tagId, tagName) ?: return null
        lead.tags.add(tag)
        leadRepository.save(lead)
        return tag.id
    }

    override fun ensurePersonTag(
        personId: UUID,
        tagId: UUID?,
        tagName: String?,
    ): UUID? {
        val person = personRepository.findActiveById(personId) ?: return null
        val tag = resolveTag(tagId, tagName) ?: return null
        person.tags.add(tag)
        personRepository.save(person)
        return tag.id
    }

    override fun createNoteActivity(
        entityType: String,
        entityId: UUID,
        title: String,
        comment: String?,
    ): UUID {
        val activity =
            activityRepository.save(
                Activity().apply {
                    this.title = title
                    this.type = ActivityType.NOTE
                    this.comment = comment
                    this.isDone = true
                    when (entityType) {
                        "Lead" -> this.leadId = entityId
                        "Person" -> this.personId = entityId
                        "Organization" -> this.organizationId = entityId
                    }
                },
            )
        return activity.id!!
    }

    override fun findLeadPersonAndEmail(leadId: UUID): Pair<UUID, String?>? {
        val lead = leadRepository.findActiveById(leadId) ?: return null
        val personId = lead.personId ?: return null
        val person = personRepository.findActiveById(personId) ?: return null
        return personId to person.email
    }

    override fun findPersonEmail(personId: UUID): String? = personRepository.findActiveById(personId)?.email

    override fun findLeadOwnerId(leadId: UUID): UUID? = leadRepository.findActiveById(leadId)?.userId

    override fun createPersonFromForm(
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        jobTitle: String?,
    ): UUID =
        personService
            .create(
                firstName = firstName.ifBlank { "Web" },
                lastName = lastName.ifBlank { "Form Submission" },
                organizationId = null,
                email = email,
                phone = phone,
                jobTitle = jobTitle,
                emails = email?.takeIf { it.isNotBlank() }?.let { listOf(ContactEntry(it)) },
                contactNumbers = phone?.takeIf { it.isNotBlank() }?.let { listOf(ContactEntry(it)) },
            ).id

    override fun createLeadFromForm(
        title: String,
        description: String?,
        amount: BigDecimal?,
        personId: UUID?,
    ): UUID? {
        val defaultPipeline =
            pipelineRepository.findAllActive().firstOrNull { it.isDefault }
                ?: pipelineRepository.findAllActive().firstOrNull()
        val defaultStage =
            defaultPipeline?.let {
                stageRepository.findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(it.id!!).firstOrNull()
            }
        if (defaultPipeline == null || defaultStage == null) {
            log.warn("Tenant has no default pipeline; skipping lead creation from form")
            return null
        }
        return leadService
            .create(
                title = title,
                description = description,
                amount = amount,
                expectedCloseDate = null,
                pipelineId = defaultPipeline.id!!,
                stageId = defaultStage.id!!,
                personId = personId,
                organizationId = null,
                leadSourceId = null,
                leadTypeId = null,
                userId = null,
            ).id
    }

    private fun resolveTag(
        tagId: UUID?,
        tagName: String?,
    ): Tag? {
        if (tagId != null) {
            return tagRepository.findById(tagId).orElse(null)
        }
        if (tagName.isNullOrBlank()) return null
        val existing = tagRepository.findAllByNameContainingIgnoreCase(tagName).firstOrNull { it.name == tagName }
        if (existing != null) return existing
        return tagRepository.save(Tag().apply { this.name = tagName })
    }
}
