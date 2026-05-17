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

    fun findById(id: UUID): TagResponse =
        tagRepository.findById(id).orElseThrow { NoSuchElementException("Tag not found: $id") }.toResponse()

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
        val tag = tagRepository.findById(id).orElseThrow { NoSuchElementException("Tag not found: $id") }
        if (tagRepository.existsByNameAndIdNot(name, id)) throw IllegalStateException("Tag name already in use: $name")
        tag.name = name
        tag.color = color
        return tagRepository.save(tag).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        if (!tagRepository.existsById(id)) throw NoSuchElementException("Tag not found: $id")
        tagRepository.deleteById(id)
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            if (tagRepository.existsById(id)) tagRepository.deleteById(id)
        }
    }
}

fun Tag.toResponse() =
    TagResponse(
        id = id!!,
        name = name,
        color = color,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
