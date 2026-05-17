package com.synopticengine.api.identity.service

import com.synopticengine.api.identity.domain.Group
import com.synopticengine.api.identity.repo.GroupRepository
import com.synopticengine.api.identity.web.GroupResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class GroupService(
    private val groupRepository: GroupRepository,
) {
    fun findAll(): List<GroupResponse> = groupRepository.findAll().map { it.toResponse() }

    fun findById(id: UUID): GroupResponse =
        groupRepository
            .findById(id)
            .orElseThrow { NoSuchElementException("Group not found: $id") }
            .toResponse()

    @Transactional
    fun create(
        name: String,
        description: String?,
    ): GroupResponse {
        if (groupRepository.existsByName(name)) throw IllegalStateException("Group name already in use: $name")
        return groupRepository
            .save(
                Group().apply {
                    this.name = name
                    this.description = description
                },
            ).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
        description: String?,
    ): GroupResponse {
        val group =
            groupRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("Group not found: $id") }
        if (groupRepository.existsByNameAndIdNot(
                name,
                id,
            )
        ) {
            throw IllegalStateException("Group name already in use: $name")
        }
        group.name = name
        group.description = description
        return groupRepository.save(group).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        if (!groupRepository.existsById(id)) throw NoSuchElementException("Group not found: $id")
        groupRepository.deleteById(id)
    }
}

fun Group.toResponse() =
    GroupResponse(
        id = id!!,
        name = name,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
