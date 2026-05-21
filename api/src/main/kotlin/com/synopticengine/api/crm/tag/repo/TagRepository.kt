package com.synopticengine.api.crm.tag.repo

import com.synopticengine.api.crm.tag.domain.Tag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TagRepository : JpaRepository<Tag, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean

    fun findAllByNameContainingIgnoreCase(query: String): List<Tag>

    fun findAllByIdIn(ids: Collection<UUID>): List<Tag>

    // Tenant-aware load. JpaRepository.findById hits `EntityManager.find()` and
    // bypasses Hibernate's `@Filter("tenantFilter")`. JPQL goes through query
    // rewriting so the filter applies. Callers must use this for IDOR safety.
    @Query("SELECT t FROM Tag t WHERE t.id = :id")
    fun findActiveById(
        @Param("id") id: UUID,
    ): Tag?
}
