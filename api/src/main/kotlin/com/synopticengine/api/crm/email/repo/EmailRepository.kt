package com.synopticengine.api.crm.email.repo

import com.synopticengine.api.crm.email.domain.Email
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EmailRepository : JpaRepository<Email, UUID> {
    // JpaRepository.findById / existsById hit Hibernate's `EntityManager.find()`
    // fast-path, which does NOT apply Hibernate's `@Filter("tenantFilter")` —
    // the filter only rewrites HQL/JPQL/Criteria query results. The methods
    // below are the tenant-aware loaders; callers in EmailService must use
    // these and never the inherited primary-key methods (they would IDOR
    // across tenants).
    @Query("SELECT e FROM Email e WHERE e.id = :id AND e.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): Email?

    @Query("SELECT COUNT(e) > 0 FROM Email e WHERE e.id = :id AND e.deletedAt IS NULL")
    fun existsActiveById(
        @Param("id") id: UUID,
    ): Boolean

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

    @Modifying
    @Query(
        """
        UPDATE Email e
        SET e.personId = :targetPersonId
        WHERE e.personId = :sourcePersonId
          AND e.deletedAt IS NULL
    """,
    )
    fun reassignPerson(
        @Param("sourcePersonId") sourcePersonId: UUID,
        @Param("targetPersonId") targetPersonId: UUID,
    ): Int

    @Query(
        """
        SELECT e FROM Email e
        WHERE e.deletedAt IS NULL
          AND e.messageId IN :messageIds
        ORDER BY e.createdAt DESC
    """,
    )
    fun findThreadParentsByMessageIds(
        @Param("messageIds") messageIds: Collection<String>,
    ): List<Email>

    @Query(
        """
        SELECT e.tenantId FROM Email e
        WHERE e.deletedAt IS NULL
          AND e.messageId IN :messageIds
        ORDER BY e.createdAt DESC
    """,
    )
    fun findTenantIdsByMessageIds(
        @Param("messageIds") messageIds: Collection<String>,
    ): List<UUID>
}
