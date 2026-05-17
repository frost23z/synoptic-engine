package com.synopticengine.api.identity.repo

import com.synopticengine.api.identity.domain.Group
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GroupRepository : JpaRepository<Group, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean

    fun findAllByIdIn(ids: Collection<UUID>): List<Group>
}
