package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.Pipeline
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface PipelineRepository : JpaRepository<Pipeline, UUID> {
    @Query("SELECT p FROM Pipeline p WHERE p.deletedAt IS NULL")
    fun findAllActive(): List<Pipeline>

    @Query("SELECT p FROM Pipeline p WHERE p.id = :id AND p.deletedAt IS NULL")
    fun findActiveById(id: UUID): Pipeline?

    fun existsByIsDefaultTrueAndDeletedAtIsNull(): Boolean

    fun findFirstByIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtAsc(): Pipeline?
}
