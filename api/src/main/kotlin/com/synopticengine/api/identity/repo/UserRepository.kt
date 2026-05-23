package com.synopticengine.api.identity.repo

import com.synopticengine.api.identity.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository :
    JpaRepository<User, UUID>,
    JpaSpecificationExecutor<User> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): User?

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    fun existsActiveById(
        @Param("id") id: UUID,
    ): Boolean

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    fun findActiveByEmail(
        @Param("email") email: String,
    ): User?

    // Returns a list intentionally: with `LEFT JOIN FETCH` on two Set collections
    // (roles, permissions), Hibernate 7's `getSingleResult` raises NonUniqueResultException
    // even when DISTINCT collapses the entity stream to one user. Take the first row at
    // the call site instead.
    @Query(
        """
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.roles r
        LEFT JOIN FETCH r.permissions
        WHERE u.email = :email
        AND u.deletedAt IS NULL
    """,
    )
    fun findActiveByEmailWithRolesAsList(email: String): List<User>

    @Query(
        """
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.roles r
        LEFT JOIN FETCH r.permissions
        WHERE u.id = :id
        AND u.deletedAt IS NULL
    """,
    )
    fun findActiveByIdWithRolesAsList(id: UUID): List<User>

    fun findAllByDeletedAtIsNull(): List<User>

    // Native query — Hibernate `@Filter("tenantFilter")` does not rewrite native
    // SQL. `user_groups` is a join table without `tenant_id`; tenant boundary
    // is enforced by joining `users` and matching on the caller's tenant.
    @Query(
        value = """
        SELECT DISTINCT ug2.user_id
        FROM user_groups ug1
        JOIN user_groups ug2 ON ug1.group_id = ug2.group_id
        JOIN users u ON u.id = ug2.user_id
        WHERE ug1.user_id = :userId
          AND u.tenant_id = :tenantId
          AND u.deleted_at IS NULL
    """,
        nativeQuery = true,
    )
    fun findGroupMemberIds(
        @Param("userId") userId: UUID,
        @Param("tenantId") tenantId: UUID,
    ): List<UUID>

    @Query(
        """
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
        AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :query, '%')))
    """,
    )
    fun searchActive(query: String): List<User>

    @Query(
        value = """
        SELECT u.id
        FROM users u
        JOIN user_groups ug ON ug.user_id = u.id
        WHERE ug.group_id = :groupId
          AND u.deleted_at IS NULL
          AND u.tenant_id = :tenantId
        ORDER BY u.created_at ASC
    """,
        nativeQuery = true,
    )
    fun findActiveIdsByGroupId(
        @Param("groupId") groupId: UUID,
        @Param("tenantId") tenantId: UUID,
    ): List<UUID>
}
