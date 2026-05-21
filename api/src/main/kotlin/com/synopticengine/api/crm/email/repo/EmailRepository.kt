package com.synopticengine.api.crm.email.repo

import com.synopticengine.api.crm.email.domain.Email
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EmailRepository : JpaRepository<Email, UUID> {
    // Native query — @SQLRestriction and Hibernate `@Filter("tenantFilter")` are
    // both bypassed for native SQL, and the `emails` table is not covered by
    // Postgres RLS (V007 only enables RLS on leads/orgs/persons/products). The
    // tenant_id predicate here is therefore the only isolation layer; callers
    // must pass TenantContext.get().
    @Query(
        nativeQuery = true,
        value =
            "SELECT * FROM emails " +
                "WHERE tenant_id = :tenantId " +
                "AND folders @> jsonb_build_array(cast(:folder as text)) " +
                "AND deleted_at IS NULL",
        countQuery =
            "SELECT count(*) FROM emails " +
                "WHERE tenant_id = :tenantId " +
                "AND folders @> jsonb_build_array(cast(:folder as text)) " +
                "AND deleted_at IS NULL",
    )
    fun findByFolder(
        @Param("tenantId") tenantId: UUID,
        @Param("folder") folder: String,
        pageable: Pageable,
    ): Page<Email>
}
