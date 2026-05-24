package com.synopticengine.api.crm.contact.repo

import com.synopticengine.api.crm.contact.domain.Organization
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface OrganizationRepository : JpaRepository<Organization, UUID> {
    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT o FROM Organization o WHERE o.id = :id AND o.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): Organization?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Organization>

    @Query(
        """
        SELECT o FROM Organization o
        WHERE o.deletedAt IS NULL
        AND (LOWER(o.name)    LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(o.email)   LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(o.phone)   LIKE LOWER(CONCAT('%', :q, '%')))
    """,
    )
    fun search(
        q: String,
        pageable: Pageable,
    ): Page<Organization>

    @Query(
        """
        SELECT o FROM Organization o
        WHERE o.deletedAt IS NULL
        AND o.createdBy IN :createdByIds
        AND (LOWER(o.name)    LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(o.email)   LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(o.phone)   LIKE LOWER(CONCAT('%', :q, '%')))
    """,
    )
    fun searchScopedByCreatedBy(
        q: String,
        createdByIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Organization>

    @Query(
        """
        SELECT o FROM Organization o
        WHERE o.deletedAt IS NULL
        AND o.createdBy IN :createdByIds
    """,
    )
    fun findAllScopedByCreatedBy(
        createdByIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Organization>

    @Query(
        value = """
            SELECT COUNT(*) FROM organizations
            WHERE deleted_at IS NULL
              AND created_at >= :start AND created_at < :end
        """,
        nativeQuery = true,
    )
    fun countCreatedInRangeNative(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
    ): Long
}
