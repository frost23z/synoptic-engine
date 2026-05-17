package com.synopticengine.api.crm.contact.repo

import com.synopticengine.api.crm.contact.domain.Person
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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
}
