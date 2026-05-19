package com.synopticengine.api.crm.contact.service

import com.synopticengine.api.crm.ActivityPage
import com.synopticengine.api.crm.ActivityPageEntry
import com.synopticengine.api.crm.ActivitySummary
import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.crm.DashboardLeadStats
import com.synopticengine.api.crm.LeadCascadeInfo
import com.synopticengine.api.crm.LeadCsvRow
import com.synopticengine.api.crm.OrganizationCsvRow
import com.synopticengine.api.crm.OrganizationSummary
import com.synopticengine.api.crm.PersonCsvRow
import com.synopticengine.api.crm.PersonSummary
import com.synopticengine.api.crm.SalespersonStats
import com.synopticengine.api.crm.StageStats
import com.synopticengine.api.crm.TagDto
import com.synopticengine.api.crm.activity.repo.ActivityParticipantRepository
import com.synopticengine.api.crm.activity.repo.ActivityRepository
import com.synopticengine.api.crm.contact.domain.ContactEntry
import com.synopticengine.api.crm.contact.domain.Person
import com.synopticengine.api.crm.contact.repo.OrganizationRepository
import com.synopticengine.api.crm.contact.repo.PersonRepository
import com.synopticengine.api.crm.lead.domain.Lead
import com.synopticengine.api.crm.lead.domain.LeadStatus
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.lead.repo.StageRepository
import com.synopticengine.api.crm.tag.repo.TagRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CrmApiImpl(
    private val personRepository: PersonRepository,
    private val organizationRepository: OrganizationRepository,
    private val leadRepository: LeadRepository,
    private val stageRepository: StageRepository,
    private val activityRepository: ActivityRepository,
    private val activityParticipantRepository: ActivityParticipantRepository,
    private val tagRepository: TagRepository,
    private val objectMapper: ObjectMapper,
) : CrmApi {
    override fun findPersonById(id: UUID): PersonSummary? =
        personRepository.findActiveById(id)?.let {
            PersonSummary(id = it.id!!, fullName = it.fullName, email = it.email)
        }

    override fun findOrganizationById(id: UUID): OrganizationSummary? =
        organizationRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.deletedAt == null }
            ?.let { OrganizationSummary(id = it.id!!, name = it.name) }

    override fun existsPersonById(id: UUID): Boolean = personRepository.findActiveById(id) != null

    @Transactional
    override fun createPerson(
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        jobTitle: String?,
    ): PersonSummary {
        val emailJson =
            if (email.isNullOrBlank()) {
                "[]"
            } else {
                objectMapper.writeValueAsString(
                    listOf(ContactEntry(email)),
                )
            }
        val phoneJson =
            if (phone.isNullOrBlank()) {
                "[]"
            } else {
                objectMapper.writeValueAsString(
                    listOf(ContactEntry(phone)),
                )
            }
        val person =
            personRepository.save(
                Person().apply {
                    this.firstName = firstName
                    this.lastName = lastName
                    this.email = email
                    this.phone = phone
                    this.emails = emailJson
                    this.contactNumbers = phoneJson
                    this.jobTitle = jobTitle
                },
            )
        return PersonSummary(id = person.id!!, fullName = person.fullName, email = person.email)
    }

    @Transactional
    override fun createLead(
        title: String,
        description: String?,
        amount: BigDecimal?,
        pipelineId: UUID,
        stageId: UUID,
    ): LeadCsvRow {
        val lead =
            leadRepository.save(
                Lead().apply {
                    this.title = title
                    this.description = description
                    this.amount = amount
                    this.pipelineId = pipelineId
                    this.stageId = stageId
                },
            )
        return LeadCsvRow(
            id = lead.id!!,
            title = lead.title,
            status = lead.status.value,
            amount = lead.amount,
            pipelineId = lead.pipelineId,
            stageId = lead.stageId,
        )
    }

    override fun exportPersonsCsv(): List<PersonCsvRow> =
        personRepository
            .findAllByDeletedAtIsNull(PageRequest.of(0, Int.MAX_VALUE))
            .content
            .map {
                PersonCsvRow(
                    id = it.id!!,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    email = it.email,
                    phone = it.phone,
                    jobTitle = it.jobTitle,
                )
            }

    override fun exportOrganizationsCsv(): List<OrganizationCsvRow> =
        organizationRepository
            .findAllByDeletedAtIsNull(PageRequest.of(0, Int.MAX_VALUE))
            .content
            .map {
                OrganizationCsvRow(
                    id = it.id!!,
                    name = it.name,
                    email = it.email,
                    phone = it.phone,
                    website = it.website,
                    address = it.address,
                )
            }

    override fun exportLeadsCsv(): List<LeadCsvRow> =
        leadRepository
            .findAllByDeletedAtIsNull(PageRequest.of(0, Int.MAX_VALUE))
            .content
            .map {
                LeadCsvRow(
                    id = it.id!!,
                    title = it.title,
                    status = it.status.value,
                    amount = it.amount,
                    pipelineId = it.pipelineId,
                    stageId = it.stageId,
                )
            }

    override fun getDashboardLeadStats(): DashboardLeadStats {
        val totalLeads = leadRepository.countByDeletedAtIsNull()
        val openLeads = leadRepository.countByStatusAndDeletedAtIsNull(LeadStatus.OPEN)
        val wonLeads = leadRepository.countByStatusAndDeletedAtIsNull(LeadStatus.WON)
        val lostLeads = leadRepository.countByStatusAndDeletedAtIsNull(LeadStatus.LOST)
        val totalRevenue = leadRepository.sumAmountByStatus(LeadStatus.WON)

        // Leads grouped by stage
        val allActiveLeads = leadRepository.findAllByDeletedAtIsNull(PageRequest.of(0, Int.MAX_VALUE)).content
        val leadsByStageId = allActiveLeads.groupBy { it.stageId }
        val allStages = stageRepository.findAll().filter { it.deletedAt == null }
        val leadsByStage =
            allStages
                .map { stage ->
                    val stageLeads = leadsByStageId[stage.id] ?: emptyList()
                    StageStats(
                        stageId = stage.id!!,
                        stageName = stage.name,
                        count = stageLeads.size,
                        value = stageLeads.sumOf { it.amount ?: BigDecimal.ZERO },
                    )
                }.filter { it.count > 0 }

        // Top salespeople (userId, count, revenue) from JPQL result
        val topRows = leadRepository.findTopSalespeople(PageRequest.of(0, 5))
        val topSalespeople =
            topRows.map { row ->
                SalespersonStats(
                    userId = row[0] as UUID,
                    leadsWon = (row[1] as Long).toInt(),
                    revenue = row[2] as BigDecimal,
                )
            }

        return DashboardLeadStats(
            totalLeads = totalLeads,
            openLeads = openLeads,
            wonLeads = wonLeads,
            lostLeads = lostLeads,
            totalRevenue = totalRevenue,
            leadsByStage = leadsByStage,
            topSalespeople = topSalespeople,
        )
    }

    override fun getRecentActivities(limit: Int): List<ActivitySummary> =
        activityRepository.findRecent(PageRequest.of(0, limit)).map { it.toSummary() }

    override fun getUpcomingActivities(limit: Int): List<ActivitySummary> =
        activityRepository.findUpcoming(Instant.now(), PageRequest.of(0, limit)).map { it.toSummary() }

    override fun findTagById(id: UUID): TagDto? =
        tagRepository.findById(id).orElse(null)?.let { TagDto(it.id!!, it.name, it.color) }

    override fun findTagsByIds(ids: Collection<UUID>): List<TagDto> =
        tagRepository.findAllById(ids).map { TagDto(it.id!!, it.name, it.color) }

    override fun tagExists(id: UUID): Boolean = tagRepository.existsById(id)

    // Bypasses Hibernate's tenantFilter — sharing.service.RecordShareService needs to
    // see the lead even when it belongs to the owner tenant whose context isn't set
    // on this query. The findById path goes through @SQLRestriction (deleted_at IS NULL)
    // only when the row's tenant_id matches the active filter. For cross-tenant lookups
    // we go straight to the repository's underlying findById which is filter-aware in a
    // session with no filter; for the share path the caller already authenticated as
    // the owner tenant via TenantContext.runAs.
    override fun findLeadCascadeInfo(leadId: UUID): LeadCascadeInfo? =
        leadRepository.findActiveById(leadId)?.let { LeadCascadeInfo(it.personId, it.organizationId) }

    override fun findLeadOwnerTenant(leadId: UUID): UUID? = leadRepository.findActiveById(leadId)?.tenantId

    override fun findPersonOwnerTenant(personId: UUID): UUID? = personRepository.findActiveById(personId)?.tenantId

    override fun findOrganizationOwnerTenant(organizationId: UUID): UUID? =
        organizationRepository
            .findById(organizationId)
            .orElse(null)
            ?.takeIf { it.deletedAt == null }
            ?.tenantId

    override fun filterActivitiesByWarehouseId(
        warehouseId: UUID,
        page: Int,
        size: Int,
    ): ActivityPage {
        val pageable = PageRequest.of(page, size, Sort.by("scheduleFrom").descending())
        val result =
            activityRepository.filter(
                leadId = null,
                personId = null,
                organizationId = null,
                userId = null,
                type = null,
                isDone = null,
                productId = null,
                warehouseId = warehouseId,
                pageable = pageable,
            )
        return ActivityPage(
            content = result.content.map { it.toPageEntry() },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size,
        )
    }

    private fun com.synopticengine.api.crm.activity.domain.Activity.toSummary() =
        ActivitySummary(
            id = id!!,
            title = title,
            type = type.name,
            isDone = isDone,
            scheduleFrom = scheduleFrom,
            scheduleTo = scheduleTo,
        )

    private fun com.synopticengine.api.crm.activity.domain.Activity.toPageEntry(): ActivityPageEntry {
        val participants = activityParticipantRepository.findAllByActivityId(id!!)
        return ActivityPageEntry(
            id = id!!,
            title = title,
            type = type.name,
            comment = comment,
            isDone = isDone,
            scheduleFrom = scheduleFrom,
            scheduleTo = scheduleTo,
            leadId = leadId,
            userId = userId,
            personId = personId,
            organizationId = organizationId,
            productId = productId,
            warehouseId = warehouseId,
            participantIds = participants.mapNotNull { it.userId },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
