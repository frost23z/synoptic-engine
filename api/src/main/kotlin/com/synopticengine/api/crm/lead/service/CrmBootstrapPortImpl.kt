package com.synopticengine.api.crm.lead.service

import com.synopticengine.api.crm.CrmBootstrapPort
import com.synopticengine.api.crm.lead.domain.LeadSource
import com.synopticengine.api.crm.lead.domain.LeadType
import com.synopticengine.api.crm.lead.domain.Pipeline
import com.synopticengine.api.crm.lead.domain.Stage
import com.synopticengine.api.crm.lead.repo.LeadSourceRepository
import com.synopticengine.api.crm.lead.repo.LeadTypeRepository
import com.synopticengine.api.crm.lead.repo.PipelineRepository
import com.synopticengine.api.crm.lead.repo.StageRepository
import com.synopticengine.api.shared.config.TenantSession
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
internal class CrmBootstrapPortImpl(
    private val pipelineRepository: PipelineRepository,
    private val stageRepository: StageRepository,
    private val leadSourceRepository: LeadSourceRepository,
    private val leadTypeRepository: LeadTypeRepository,
    private val tenantSession: TenantSession,
) : CrmBootstrapPort {
    override fun seedDefaultPipeline() {
        tenantSession.applyFilter()
        if (pipelineRepository.existsByIsDefaultTrueAndDeletedAtIsNull()) return

        val pipeline =
            pipelineRepository.save(
                Pipeline().apply {
                    name = "Default Pipeline"
                    description = "Standard sales pipeline"
                    isActive = true
                    isDefault = true
                    rottenDays = 30
                },
            )

        val defaults =
            listOf(
                "New" to (1 to 10),
                "Qualified" to (2 to 30),
                "Proposal" to (3 to 60),
                "Negotiation" to (4 to 80),
                "Won" to (5 to 100),
                "Lost" to (6 to 0),
            )
        val codes = mapOf("Won" to "won", "Lost" to "lost")

        defaults.forEach { (stageName, sortAndProbability) ->
            val (sort, probability) = sortAndProbability
            stageRepository.save(
                Stage().apply {
                    this.pipeline = pipeline
                    this.name = stageName
                    this.sortOrder = sort
                    this.probability = probability
                    this.code = codes[stageName]
                },
            )
        }
    }

    override fun seedDefaultLeadSources() {
        tenantSession.applyFilter()
        listOf("Website", "Referral", "Cold Outreach", "Social Media", "Event").forEach { name ->
            if (!leadSourceRepository.existsByName(name)) {
                leadSourceRepository.save(LeadSource().apply { this.name = name })
            }
        }
    }

    override fun seedDefaultLeadTypes() {
        tenantSession.applyFilter()
        listOf("Inbound", "Outbound", "Partner").forEach { name ->
            if (!leadTypeRepository.existsByName(name)) {
                leadTypeRepository.save(LeadType().apply { this.name = name })
            }
        }
    }
}
