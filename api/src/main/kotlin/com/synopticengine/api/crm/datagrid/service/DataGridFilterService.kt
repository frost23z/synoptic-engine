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
    ): DataGridFilterResponse {
        val normalizedSrc = src.trim().lowercase()
        validateSource(normalizedSrc)
        validateApplied(normalizedSrc, applied)
        return repository
            .save(
                DataGridSavedFilter().apply {
                    this.userId = userId
                    this.name = name
                    this.src = normalizedSrc
                    this.applied = applied
                },
            ).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        userId: UUID,
        name: String,
        applied: Map<String, Any>,
    ): DataGridFilterResponse {
        val filter = requireFilter(id, userId)
        validateApplied(filter.src, applied)
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

    private fun validateSource(src: String) {
        if (src !in ALLOWED_SOURCES) {
            throw IllegalArgumentException("Unsupported datagrid source: $src")
        }
    }

    private fun validateApplied(
        src: String,
        applied: Map<String, Any>,
    ) {
        if (applied.size > MAX_FILTER_KEYS) {
            throw IllegalArgumentException("Too many applied filters")
        }
        val allowedKeys = SOURCE_ALLOWED_KEYS[src] ?: COMMON_ALLOWED_KEYS
        applied.forEach { (key, value) ->
            if (key.length > 64 || key !in allowedKeys) {
                throw IllegalArgumentException("Unsupported filter key '$key' for source '$src'")
            }
            validateAppliedValue(value, key)
        }
    }

    private fun validateAppliedValue(
        value: Any,
        key: String,
    ) {
        when (value) {
            is String, is Number, is Boolean -> return
            is List<*> -> {
                if (value.size > MAX_FILTER_LIST_VALUES) {
                    throw IllegalArgumentException("Too many values for filter '$key'")
                }
                if (value.any { it !is String && it !is Number && it !is Boolean }) {
                    throw IllegalArgumentException("Unsupported value type in filter '$key'")
                }
                return
            }

            else -> throw IllegalArgumentException("Unsupported filter value type for '$key'")
        }
    }

    private companion object {
        const val MAX_FILTER_KEYS = 50
        const val MAX_FILTER_LIST_VALUES = 50

        val ALLOWED_SOURCES =
            setOf(
                "leads",
                "persons",
                "organizations",
                "products",
                "quotes",
                "activities",
                "emails",
                "warehouses",
            )

        val COMMON_ALLOWED_KEYS =
            setOf(
                "status",
                "q",
                "search",
                "name",
                "title",
                "email",
                "phone",
                "createdAt",
                "updatedAt",
                "ownerId",
                "userId",
                "tagId",
                "isRead",
                "folder",
                "from",
                "to",
            )

        val SOURCE_ALLOWED_KEYS =
            mapOf(
                "leads" to (COMMON_ALLOWED_KEYS + setOf("pipelineId", "stageId", "personId", "organizationId", "sourceId", "typeId")),
                "persons" to (COMMON_ALLOWED_KEYS + setOf("organizationId")),
                "organizations" to (COMMON_ALLOWED_KEYS + setOf("country", "state", "city")),
                "products" to (COMMON_ALLOWED_KEYS + setOf("sku", "price", "inventory")),
                "quotes" to (COMMON_ALLOWED_KEYS + setOf("leadId", "validUntil", "grandTotal")),
                "activities" to (COMMON_ALLOWED_KEYS + setOf("type", "startDate", "endDate", "isDone")),
                "emails" to (COMMON_ALLOWED_KEYS + setOf("messageId")),
                "warehouses" to (COMMON_ALLOWED_KEYS + setOf("code")),
            )
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
