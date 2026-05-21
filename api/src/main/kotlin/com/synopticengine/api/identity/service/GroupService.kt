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

    fun findById(id: UUID): GroupResponse = requireGroup(id).toResponse()

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
        val group = requireGroup(id)
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
        groupRepository.delete(requireGroup(id))
    }

    // Tenant-aware load. See EmailService.requireEmail for the IDOR rationale.
    private fun requireGroup(id: UUID): Group =
        groupRepository.findActiveById(id) ?: throw NoSuchElementException("Group not found: $id")
}

fun Group.toResponse() =
    GroupResponse(
        id = id!!,
        name = name,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
