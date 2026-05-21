package com.synopticengine.api.settings.config.repo

import com.synopticengine.api.settings.config.domain.SystemConfig
import com.synopticengine.api.settings.config.domain.SystemConfigId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SystemConfigRepository : JpaRepository<SystemConfig, SystemConfigId> {
    // Ordered list — the Hibernate tenant filter is what scopes by tenant; no
    // explicit `WHERE tenant_id` is needed in JPQL because the filter applies.
    fun findAllByOrderByGroupNameAscSortOrderAsc(): List<SystemConfig>

    // Tenant-aware single lookup. Avoids `findById(SystemConfigId)` because
    // that hits EntityManager.find() and bypasses Hibernate's tenant filter —
    // and the caller never supplies the tenantId themselves: the filter
    // injects it from TenantContext.
    @Query("SELECT c FROM SystemConfig c WHERE c.code = :code")
    fun findByCode(
        @Param("code") code: String,
    ): SystemConfig?

    // Used by TenantProvisioning to copy catalogue rows from the seed tenant
    // into a freshly-provisioned tenant. Native query so it can run with
    // TenantContext set to the seed tenant when callers explicitly want the
    // catalog without filtering side-effects.
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM system_configs WHERE tenant_id = :tenantId ORDER BY group_name, sort_order",
    )
    fun findAllForTenantRaw(
        @Param("tenantId") tenantId: UUID,
    ): List<SystemConfig>
}
