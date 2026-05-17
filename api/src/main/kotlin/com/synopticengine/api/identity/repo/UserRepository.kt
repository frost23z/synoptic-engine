package com.synopticengine.api.identity.repo

import com.synopticengine.api.identity.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface UserRepository :
    JpaRepository<User, UUID>,
    JpaSpecificationExecutor<User> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    @Query(
        """
        SELECT u FROM User u
        LEFT JOIN FETCH u.roles r
        LEFT JOIN FETCH r.permissions
        WHERE u.email = :email
        AND u.deletedAt IS NULL
    """,
    )
    fun findActiveByEmailWithRoles(email: String): User?

    @Query(
        """
        SELECT u FROM User u
        LEFT JOIN FETCH u.roles r
        LEFT JOIN FETCH r.permissions
        WHERE u.id = :id
        AND u.deletedAt IS NULL
    """,
    )
    fun findActiveByIdWithRoles(id: UUID): User?

    fun findAllByDeletedAtIsNull(): List<User>

    @Query(
        value = """
        SELECT DISTINCT ug2.user_id
        FROM user_groups ug1
        JOIN user_groups ug2 ON ug1.group_id = ug2.group_id
        WHERE ug1.user_id = :userId
    """,
        nativeQuery = true,
    )
    fun findGroupMemberIds(userId: UUID): List<UUID>

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
}
