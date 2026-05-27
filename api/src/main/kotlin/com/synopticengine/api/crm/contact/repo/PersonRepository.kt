package com.synopticengine.api.crm.contact.repo

import com.synopticengine.api.crm.contact.domain.Person
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PersonRepository : JpaRepository<Person, UUID> {
    @Query("SELECT p FROM Person p LEFT JOIN FETCH p.tags WHERE p.id = :id AND p.deletedAt IS NULL")
    fun findActiveById(id: UUID): Person?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Person>

    @Query(
        """
        SELECT p FROM Person p
        WHERE p.deletedAt IS NULL
        AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(p.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(p.email)     LIKE LOWER(CONCAT('%', :q, '%')))
    """,
    )
    fun search(
        q: String,
        pageable: Pageable,
    ): Page<Person>

    @Query(
        """
        SELECT p FROM Person p
        WHERE p.deletedAt IS NULL
        AND p.createdBy IN :createdByIds
        AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(p.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(p.email)     LIKE LOWER(CONCAT('%', :q, '%')))
    """,
    )
    fun searchScopedByCreatedBy(
        q: String,
        createdByIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Person>

    fun findAllByOrganizationIdAndDeletedAtIsNull(
        organizationId: UUID,
        pageable: Pageable,
    ): Page<Person>

    @Query(
        """
        SELECT p FROM Person p
        WHERE p.deletedAt IS NULL
        AND p.createdBy IN :createdByIds
    """,
    )
    fun findAllScopedByCreatedBy(
        createdByIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Person>

    /**
     * Count persons created in the given time window for a specific tenant.
     * The explicit `tenant_id = :tenantId` predicate mirrors
     * [ActivityRepository.countCreatedInRangeNative] — native SQL bypasses the
     * Hibernate `@Filter`, so we add the predicate explicitly. T2.2.
     */
    @Query(
        value = """
            SELECT COUNT(*) FROM persons
            WHERE deleted_at IS NULL
              AND tenant_id = :tenantId
              AND created_at >= :start AND created_at < :end
        """,
        nativeQuery = true,
    )
    fun countCreatedInRangeNative(
        @Param("tenantId") tenantId: UUID,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
    ): Long

    @Modifying
    @Query(
        value = """
            INSERT INTO person_tags (person_id, tag_id)
            VALUES (:personId, :tagId)
            ON CONFLICT DO NOTHING
        """,
        nativeQuery = true,
    )
    fun attachTag(
        @Param("personId") personId: UUID,
        @Param("tagId") tagId: UUID,
    ): Int

    // T5.2 — replace per-entity find+save loop with a single UPDATE statement.
    @Modifying
    @Query("UPDATE Person p SET p.deletedAt = :now WHERE p.id IN :ids AND p.deletedAt IS NULL")
    fun bulkSoftDelete(
        @Param("ids") ids: Collection<UUID>,
        @Param("now") now: Instant,
    ): Int
}
