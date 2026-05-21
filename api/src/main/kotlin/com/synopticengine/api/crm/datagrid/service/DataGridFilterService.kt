package com.synopticengine.api.crm.datagrid.service

import com.synopticengine.api.crm.datagrid.domain.DataGridSavedFilter
import com.synopticengine.api.crm.datagrid.repo.DataGridSavedFilterRepository
import com.synopticengine.api.crm.datagrid.web.DataGridFilterResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class DataGridFilterService(
    private val repository: DataGridSavedFilterRepository,
) {
    fun findByUserAndSrc(
        userId: UUID,
        src: String?,
    ): List<DataGridFilterResponse> =
        if (src != null) {
            repository.findAllByUserIdAndSrc(userId, src)
        } else {
            repository.findAllByUserId(userId)
        }.map { it.toResponse() }

    @Transactional
    fun save(
        userId: UUID,
        name: String,
        src: String,
        applied: Map<String, Any>,
    ): DataGridFilterResponse =
        repository
            .save(
                DataGridSavedFilter().apply {
                    this.userId = userId
                    this.name = name
                    this.src = src
                    this.applied = applied
                },
            ).toResponse()

    @Transactional
    fun update(
        id: UUID,
        userId: UUID,
        name: String,
        applied: Map<String, Any>,
    ): DataGridFilterResponse {
        val filter = requireFilter(id, userId)
        filter.name = name
        filter.applied = applied
        return repository.save(filter).toResponse()
    }

    @Transactional
    fun delete(
        id: UUID,
        userId: UUID,
    ) {
        val filter = requireFilter(id, userId)
        repository.delete(filter)
    }

    // Tenant-aware load (JPQL); the user-id check is the additional per-user
    // authorization on top of tenant scoping.
    private fun requireFilter(
        id: UUID,
        userId: UUID,
    ): DataGridSavedFilter {
        val filter = repository.findActiveById(id) ?: throw NoSuchElementException("Filter not found: $id")
        if (filter.userId != userId) throw IllegalArgumentException("Filter not found: $id")
        return filter
    }
}

fun DataGridSavedFilter.toResponse() =
    DataGridFilterResponse(
        id = id!!,
        userId = userId,
        name = name,
        src = src,
        applied = applied,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
