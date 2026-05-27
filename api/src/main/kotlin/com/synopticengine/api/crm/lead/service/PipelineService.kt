package com.synopticengine.api.crm.lead.service

import com.synopticengine.api.crm.lead.domain.Pipeline
import com.synopticengine.api.crm.lead.domain.Stage
import com.synopticengine.api.crm.lead.repo.PipelineRepository
import com.synopticengine.api.crm.lead.repo.StageRepository
import com.synopticengine.api.crm.lead.web.PipelineResponse
import com.synopticengine.api.crm.lead.web.StageOrderEntry
import com.synopticengine.api.crm.lead.web.StageResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PipelineService(
    private val pipelineRepository: PipelineRepository,
    private val stageRepository: StageRepository,
    private val leadRepository: com.synopticengine.api.crm.lead.repo.LeadRepository,
) {
    fun findAll(): List<PipelineResponse> {
        val pipelines = pipelineRepository.findAllActive()
        return pipelines.map { pipeline ->
            val stages = stageRepository.findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(pipeline.id!!)
            pipeline.toResponseWithStages(stages)
        }
    }

    fun findById(id: UUID): PipelineResponse {
        val pipeline = pipelineRepository.findActiveById(id) ?: throw NoSuchElementException("Pipeline not found: $id")
        val stages = stageRepository.findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(pipeline.id!!)
        return pipeline.toResponseWithStages(stages)
    }

    @Transactional
    fun create(
        name: String,
        description: String?,
        isActive: Boolean,
        rottenDays: Int,
    ): PipelineResponse {
        val pipeline =
            pipelineRepository.save(
                Pipeline().apply {
                    this.name = name
                    this.description = description
                    this.isActive = isActive
                    this.rottenDays = rottenDays
                },
            )
        return pipeline.toResponseWithStages(emptyList())
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
        description: String?,
        isActive: Boolean,
        isDefault: Boolean,
        rottenDays: Int,
    ): PipelineResponse {
        val pipeline = requirePipeline(id)
        pipeline.name = name
        pipeline.description = description
        pipeline.isActive = isActive
        pipeline.isDefault = isDefault
        pipeline.rottenDays = rottenDays
        pipelineRepository.save(pipeline)
        val stages = stageRepository.findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(pipeline.id!!)
        return pipeline.toResponseWithStages(stages)
    }

    // T5.2 — replaced load-all-leads+loop with existsBy check + single bulk UPDATE.
    @Transactional
    fun delete(id: UUID) {
        val pipeline = requirePipeline(id)
        if (pipeline.isDefault) throw IllegalStateException("Cannot delete the default pipeline")
        if (leadRepository.existsByPipelineIdAndDeletedAtIsNull(id)) {
            val defaultPipeline =
                pipelineRepository.findAllActive().firstOrNull { it.isDefault }
                    ?: throw IllegalStateException("No default pipeline configured; cannot reparent leads.")
            val firstStage =
                stageRepository
                    .findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(defaultPipeline.id!!)
                    .firstOrNull()
                    ?: throw IllegalStateException("Default pipeline has no stages; cannot reparent leads.")
            leadRepository.bulkReparentToDefaultPipeline(id, defaultPipeline.id!!, firstStage.id!!)
        }
        pipeline.deletedAt = Instant.now()
        pipelineRepository.save(pipeline)
    }

    @Transactional
    fun addStage(
        pipelineId: UUID,
        name: String,
        sortOrder: Int,
        color: String?,
        probability: Int,
        code: String?,
    ): StageResponse {
        val pipeline = requirePipeline(pipelineId)
        val stage =
            stageRepository.save(
                Stage().apply {
                    this.pipeline = pipeline
                    this.pipelineId = pipeline.id!! // set explicitly so cache returns correct value
                    this.name = name
                    this.sortOrder = sortOrder
                    this.color = color
                    this.probability = probability
                    this.code = code
                },
            )
        return stage.toResponse()
    }

    @Transactional
    fun updateStage(
        pipelineId: UUID,
        stageId: UUID,
        name: String,
        sortOrder: Int,
        color: String?,
        probability: Int,
        code: String?,
    ): StageResponse {
        requirePipeline(pipelineId)
        val stage =
            stageRepository.findByIdAndDeletedAtIsNull(stageId)
                ?: throw NoSuchElementException("Stage not found: $stageId")
        stage.name = name
        stage.sortOrder = sortOrder
        stage.color = color
        stage.probability = probability
        stage.code = code
        return stageRepository.save(stage).toResponse()
    }

    // T5.2 — replaced load-all-leads+loop with a single bulk UPDATE.
    @Transactional
    fun deleteStage(
        pipelineId: UUID,
        stageId: UUID,
    ) {
        requirePipeline(pipelineId)
        val stage =
            stageRepository.findByIdAndDeletedAtIsNull(stageId)
                ?: throw NoSuchElementException("Stage not found: $stageId")
        val siblingStages =
            stageRepository
                .findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(pipelineId)
                .filter { it.id != stageId }
        if (siblingStages.isEmpty()) {
            throw IllegalStateException("Cannot delete the last stage in a pipeline.")
        }
        val target = siblingStages.first()
        leadRepository.bulkReparentToStage(stageId, pipelineId, target.id!!)
        stage.deletedAt = Instant.now()
        stageRepository.save(stage)
    }

    @Transactional
    fun reorderStages(
        pipelineId: UUID,
        order: List<StageOrderEntry>,
    ): PipelineResponse {
        pipelineRepository.findActiveById(pipelineId)
            ?: throw NoSuchElementException("Pipeline not found: $pipelineId")
        val stageMap = stageRepository.findAllByIdIn(order.map { it.id }).associateBy { it.id!! }
        order.forEach { entry ->
            stageMap[entry.id]?.let { stage ->
                stage.sortOrder = entry.sortOrder
                stageRepository.save(stage)
            }
        }
        val stages = stageRepository.findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(pipelineId)
        return pipelineRepository.findActiveById(pipelineId)!!.toResponseWithStages(stages)
    }

    private fun requirePipeline(id: UUID): Pipeline =
        pipelineRepository.findActiveById(id) ?: throw NoSuchElementException("Pipeline not found: $id")
}

fun Pipeline.toResponseWithStages(stages: List<Stage>): PipelineResponse =
    PipelineResponse(
        id = id!!,
        name = name,
        description = description,
        isActive = isActive,
        isDefault = isDefault,
        rottenDays = rottenDays,
        stages = stages.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Stage.toResponse(): StageResponse =
    StageResponse(
        id = id!!,
        pipelineId = pipelineId,
        name = name,
        sortOrder = sortOrder,
        color = color,
        probability = probability,
        code = code,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
