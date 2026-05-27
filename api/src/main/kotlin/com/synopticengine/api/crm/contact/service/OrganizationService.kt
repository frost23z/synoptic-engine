package com.synopticengine.api.crm.contact.service

import com.synopticengine.api.crm.contact.domain.Organization
import com.synopticengine.api.crm.contact.repo.OrganizationRepository
import com.synopticengine.api.crm.contact.web.OrganizationResponse
import com.synopticengine.api.crm.scoping.ScopeResolver
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val scopeResolver: ScopeResolver,
) {
    fun findAll(pageable: Pageable): PageResponse<OrganizationResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(organizationRepository.findAllByDeletedAtIsNull(pageable)) { it.toResponse() }
        } else {
            PageResponse.of(organizationRepository.findAllScopedByCreatedBy(scopeIds, pageable)) { it.toResponse() }
        }
    }

    fun findById(id: UUID): OrganizationResponse = requireOrg(id).toResponse()

    fun search(
        q: String,
        pageable: Pageable,
    ): PageResponse<OrganizationResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        return if (scopeIds == null) {
            PageResponse.of(organizationRepository.search(q, pageable)) { it.toResponse() }
        } else {
            PageResponse.of(organizationRepository.searchScopedByCreatedBy(q, scopeIds, pageable)) { it.toResponse() }
        }
    }

    @Transactional
    fun create(
        name: String,
        email: String?,
        phone: String?,
        website: String?,
        address: String?,
    ): OrganizationResponse =
        organizationRepository
            .save(
                Organization().apply {
                    this.name = name
                    this.email = email
                    this.phone = phone
                    this.website = website
                    this.address = address
                },
            ).toResponse()

    @Transactional
    fun update(
        id: UUID,
        name: String,
        email: String?,
        phone: String?,
        website: String?,
        address: String?,
    ): OrganizationResponse {
        val org = requireOrg(id)
        org.name = name
        org.email = email
        org.phone = phone
        org.website = website
        org.address = address
        return organizationRepository.save(org).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val org = requireOrg(id)
        org.deletedAt = Instant.now()
        organizationRepository.save(org)
    }

    // T5.2 — one UPDATE instead of N find+save round-trips.
    @Transactional
    fun massDestroy(ids: List<UUID>) {
        if (ids.isEmpty()) return
        organizationRepository.bulkSoftDelete(ids, Instant.now())
    }

    // Tenant-aware load. See EmailService.requireEmail for the IDOR rationale.
    private fun requireOrg(id: UUID): Organization =
        organizationRepository.findActiveById(id)
            ?: throw NoSuchElementException("Organization not found: $id")
}

fun Organization.toResponse() =
    OrganizationResponse(
        id = id!!,
        name = name,
        email = email,
        phone = phone,
        website = website,
        address = address,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
