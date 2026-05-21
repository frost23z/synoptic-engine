package com.synopticengine.api.identity.repo

import com.synopticengine.api.identity.domain.Group
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface GroupRepository : JpaRepository<Group, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean

    fun findAllByIdIn(ids: Collection<UUID>): List<Group>

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT g FROM Group g WHERE g.id = :id")
    fun findActiveById(
        @Param("id") id: UUID,
    ): Group?
}
