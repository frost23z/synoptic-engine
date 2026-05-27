package com.synopticengine.api.settings.config.service

import com.synopticengine.api.settings.config.domain.SystemConfig
import com.synopticengine.api.settings.config.repo.SystemConfigRepository
import com.synopticengine.api.settings.config.web.SystemConfigGroupResponse
import com.synopticengine.api.settings.config.web.SystemConfigResponse
import com.synopticengine.api.shared.audit.AuditAction
import com.synopticengine.api.shared.audit.AuditLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class SystemConfigService(
    private val repository: SystemConfigRepository,
    private val auditLogService: AuditLogService,
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
        val saved = repository.save(config)

        // T3.4 — audit sensitive-field mutations.  For secret configs we omit
        // the value from the payload — it is already masked in the response.
        auditLogService.record(
            entityType = "system_config",
            entityId = code,
            action = AuditAction.UPDATE,
            payload =
                mapOf(
                    "code" to code,
                    "groupName" to config.groupName,
                    "isSecret" to config.isSecret,
                    // Mask secret values in the audit payload — the audit log
                    // itself is tenant-visible and should not expose credentials.
                    "value" to if (config.isSecret) "***" else value,
                ),
        )

        return saved.toResponse()
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
