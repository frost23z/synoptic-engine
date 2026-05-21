package com.synopticengine.api.crm.tag.service

import com.synopticengine.api.crm.tag.domain.Tag
import com.synopticengine.api.crm.tag.repo.TagRepository
import com.synopticengine.api.crm.tag.web.TagResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class TagService(
    private val tagRepository: TagRepository,
) {
    fun findAll(): List<TagResponse> = tagRepository.findAll().map { it.toResponse() }

    fun findById(id: UUID): TagResponse = requireTag(id).toResponse()

    fun search(q: String): List<TagResponse> =
        tagRepository.findAllByNameContainingIgnoreCase(q).map { it.toResponse() }

    @Transactional
    fun create(
        name: String,
        color: String?,
    ): TagResponse {
        if (tagRepository.existsByName(name)) throw IllegalStateException("Tag name already in use: $name")
        return tagRepository
            .save(
                Tag().apply {
                    this.name = name
                    this.color = color
                },
            ).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
        color: String?,
    ): TagResponse {
        val tag = requireTag(id)
        if (tagRepository.existsByNameAndIdNot(name, id)) throw IllegalStateException("Tag name already in use: $name")
        tag.name = name
        tag.color = color
        return tagRepository.save(tag).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        // Load through the tenant-aware finder then delete the entity (not by id)
        // so the filter actually runs before the DELETE.
        tagRepository.delete(requireTag(id))
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            tagRepository.findActiveById(id)?.let { tagRepository.delete(it) }
        }
    }

    // Tenant-aware load. See EmailService.requireEmail for the IDOR rationale.
    private fun requireTag(id: UUID): Tag =
        tagRepository.findActiveById(id) ?: throw NoSuchElementException("Tag not found: $id")
}

fun Tag.toResponse() =
    TagResponse(
        id = id!!,
        name = name,
        color = color,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
