package com.synopticengine.api.crm.activity.repo

import com.synopticengine.api.crm.activity.domain.Activity
import com.synopticengine.api.crm.activity.domain.ActivityType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface ActivityRepository : JpaRepository<Activity, UUID> {
    fun findByIdAndDeletedAtIsNull(id: UUID): Activity?

    @Query(
        """
        SELECT a FROM Activity a
        WHERE a.deletedAt IS NULL
        AND (:leadId IS NULL OR a.leadId = :leadId)
        AND (:personId IS NULL OR a.personId = :personId)
        AND (:organizationId IS NULL OR a.organizationId = :organizationId)
        AND (:userId IS NULL OR a.userId = :userId)
        AND (:type IS NULL OR a.type = :type)
        AND (:isDone IS NULL OR a.isDone = :isDone)
        AND (:productId IS NULL OR a.productId = :productId)
        AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId)
    """,
    )
    fun filter(
        leadId: UUID?,
        personId: UUID?,
        organizationId: UUID?,
        userId: UUID?,
        type: ActivityType?,
        isDone: Boolean?,
        productId: UUID?,
        warehouseId: UUID?,
        pageable: Pageable,
    ): Page<Activity>

    @Query(
        "SELECT a FROM Activity a WHERE a.deletedAt IS NULL ORDER BY a.createdAt DESC",
    )
    fun findRecent(pageable: Pageable): List<Activity>

    @Query(
        "SELECT a FROM Activity a WHERE a.deletedAt IS NULL AND a.isDone = false AND a.scheduleFrom > :now ORDER BY a.scheduleFrom ASC",
    )
    fun findUpcoming(
        now: Instant,
        pageable: Pageable,
    ): List<Activity>
}
