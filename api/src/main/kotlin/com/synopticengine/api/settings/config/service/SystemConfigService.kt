package com.synopticengine.api.settings.config.service

import com.synopticengine.api.settings.config.domain.SystemConfig
import com.synopticengine.api.settings.config.repo.SystemConfigRepository
import com.synopticengine.api.settings.config.web.SystemConfigGroupResponse
import com.synopticengine.api.settings.config.web.SystemConfigResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class SystemConfigService(
    private val repository: SystemConfigRepository,
) {
    fun findAll(): List<SystemConfigGroupResponse> {
        val configs = repository.findAllByOrderByGroupNameAscSortOrderAsc()
        return configs
            .groupBy { it.groupName }
            .map { (group, items) ->
                SystemConfigGroupResponse(
                    group = group,
                    items = items.map { it.toResponse() },
                )
            }
    }

    fun findByCode(code: String): SystemConfigResponse =
        (repository.findByCode(code) ?: throw NoSuchElementException("Config not found: $code")).toResponse()

    @Transactional
    fun update(
        code: String,
        value: String?,
    ): SystemConfigResponse {
        // findByCode runs through the Hibernate tenant filter, so a caller
        // cannot reach another tenant's config row even if they know the code.
        val config =
            repository.findByCode(code)
                ?: throw NoSuchElementException("Config not found: $code")
        config.value = value
        config.updatedAt = Instant.now()
        return repository.save(config).toResponse()
    }
}

fun SystemConfig.toResponse() =
    SystemConfigResponse(
        code = code,
        value = if (isSecret && value != null) "***" else value,
        groupName = groupName,
        label = label,
        type = type,
        isSecret = isSecret,
        sortOrder = sortOrder,
    )
