package com.synopticengine.api.crm.tag.repo

import com.synopticengine.api.crm.tag.domain.Tag
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TagRepository : JpaRepository<Tag, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean

    fun findAllByNameContainingIgnoreCase(query: String): List<Tag>

    fun findAllByIdIn(ids: Collection<UUID>): List<Tag>
}
