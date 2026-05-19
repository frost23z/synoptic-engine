package com.synopticengine.api.crm

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ActivityPageEntry(
    val id: UUID,
    val title: String,
    val type: String,
    val comment: String?,
    val isDone: Boolean,
    val scheduleFrom: Instant?,
    val scheduleTo: Instant?,
    val leadId: UUID?,
    val userId: UUID?,
    val personId: UUID?,
    val organizationId: UUID?,
    val productId: UUID?,
    val warehouseId: UUID?,
    val participantIds: List<UUID>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class ActivityPage(
    val content: List<ActivityPageEntry>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

data class PersonSummary(
    val id: UUID,
    val fullName: String,
    val email: String?,
)

data class OrganizationSummary(
    val id: UUID,
    val name: String,
)

data class PersonCsvRow(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val jobTitle: String?,
)

data class OrganizationCsvRow(
    val id: UUID,
    val name: String,
    val email: String?,
    val phone: String?,
    val website: String?,
    val address: String?,
)

data class LeadCsvRow(
    val id: UUID,
    val title: String,
    val status: String,
    val amount: java.math.BigDecimal?,
    val pipelineId: UUID,
    val stageId: UUID,
)

/** Minimum fields needed to apply [CascadeRules] when a lead is shared. */
data class LeadCascadeInfo(
    val personId: UUID?,
    val organizationId: UUID?,
)

interface CrmApi {
    fun findPersonById(id: UUID): PersonSummary?

    fun findOrganizationById(id: UUID): OrganizationSummary?

    fun existsPersonById(id: UUID): Boolean

    fun createPerson(
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        jobTitle: String?,
    ): PersonSummary

    fun createLead(
        title: String,
        description: String?,
        amount: java.math.BigDecimal?,
        pipelineId: UUID,
        stageId: UUID,
    ): LeadCsvRow

    fun exportPersonsCsv(): List<PersonCsvRow>

    fun exportOrganizationsCsv(): List<OrganizationCsvRow>

    fun exportLeadsCsv(): List<LeadCsvRow>

    fun getDashboardLeadStats(): DashboardLeadStats

    fun getRecentActivities(limit: Int): List<ActivitySummary>

    fun getUpcomingActivities(limit: Int): List<ActivitySummary>

    fun filterActivitiesByWarehouseId(
        warehouseId: UUID,
        page: Int,
        size: Int,
    ): ActivityPage

    fun findTagById(id: UUID): TagDto?

    fun findTagsByIds(ids: Collection<UUID>): List<TagDto>

    fun tagExists(id: UUID): Boolean

    /** For cascade resolution. Returns null if the lead doesn't exist or isn't in the current tenant. */
    fun findLeadCascadeInfo(leadId: UUID): LeadCascadeInfo?

    /** Owner of a given record — needed by sharing.service.RecordShareService for FK + cascade. */
    fun findLeadOwnerTenant(leadId: UUID): UUID?

    fun findPersonOwnerTenant(personId: UUID): UUID?

    fun findOrganizationOwnerTenant(organizationId: UUID): UUID?
}

data class StageStats(
    val stageId: UUID,
    val stageName: String,
    val count: Int,
    val value: BigDecimal,
)

data class SalespersonStats(
    val userId: UUID,
    val leadsWon: Int,
    val revenue: BigDecimal,
)

data class DashboardLeadStats(
    val totalLeads: Int,
    val openLeads: Int,
    val wonLeads: Int,
    val lostLeads: Int,
    val totalRevenue: BigDecimal,
    val leadsByStage: List<StageStats>,
    val topSalespeople: List<SalespersonStats>,
)

data class ActivitySummary(
    val id: UUID,
    val title: String,
    val type: String,
    val isDone: Boolean,
    val scheduleFrom: Instant?,
    val scheduleTo: Instant?,
)

data class TagDto(
    val id: UUID,
    val name: String,
    val color: String?,
)
