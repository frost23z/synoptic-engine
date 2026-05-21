package com.synopticengine.api.crm.lead.service

import com.synopticengine.api.crm.lead.domain.LeadSource
import com.synopticengine.api.crm.lead.domain.LeadType
import com.synopticengine.api.crm.lead.repo.LeadSourceRepository
import com.synopticengine.api.crm.lead.repo.LeadTypeRepository
import com.synopticengine.api.crm.lead.web.LeadSourceResponse
import com.synopticengine.api.crm.lead.web.LeadTypeResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class LeadSourceService(
    private val leadSourceRepository: LeadSourceRepository,
) {
    fun findAll(): List<LeadSourceResponse> = leadSourceRepository.findAll().map { it.toResponse() }

    @Transactional
    fun create(name: String): LeadSourceResponse {
        if (leadSourceRepository.existsByName(name)) throw IllegalStateException("Lead source already exists: $name")
        return leadSourceRepository.save(LeadSource().apply { this.name = name }).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
    ): LeadSourceResponse {
        val source =
            leadSourceRepository.findActiveById(id)
                ?: throw NoSuchElementException("Lead source not found: $id")
        if (leadSourceRepository.existsByNameAndIdNot(
                name,
                id,
            )
        ) {
            throw IllegalStateException("Lead source already exists: $name")
        }
        source.name = name
        return leadSourceRepository.save(source).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val source =
            leadSourceRepository.findActiveById(id)
                ?: throw NoSuchElementException("Lead source not found: $id")
        leadSourceRepository.delete(source)
    }
}

@Service
@Transactional(readOnly = true)
class LeadTypeService(
    private val leadTypeRepository: LeadTypeRepository,
) {
    fun findAll(): List<LeadTypeResponse> = leadTypeRepository.findAll().map { it.toResponse() }

    @Transactional
    fun create(name: String): LeadTypeResponse {
        if (leadTypeRepository.existsByName(name)) throw IllegalStateException("Lead type already exists: $name")
        return leadTypeRepository.save(LeadType().apply { this.name = name }).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
    ): LeadTypeResponse {
        val type =
            leadTypeRepository.findActiveById(id)
                ?: throw NoSuchElementException("Lead type not found: $id")
        if (leadTypeRepository.existsByNameAndIdNot(
                name,
                id,
            )
        ) {
            throw IllegalStateException("Lead type already exists: $name")
        }
        type.name = name
        return leadTypeRepository.save(type).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val type =
            leadTypeRepository.findActiveById(id)
                ?: throw NoSuchElementException("Lead type not found: $id")
        leadTypeRepository.delete(type)
    }
}

fun LeadSource.toResponse() = LeadSourceResponse(id = id!!, name = name, createdAt = createdAt)

fun LeadType.toResponse() = LeadTypeResponse(id = id!!, name = name, createdAt = createdAt)
