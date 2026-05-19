package com.synopticengine.api.crm.activity.repo

import com.synopticengine.api.crm.activity.domain.Activity
import com.synopticengine.api.crm.activity.domain.ActivityType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Query(
        value = """
            SELECT COUNT(*) FROM activities
            WHERE deleted_at IS NULL
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
        """,
        nativeQuery = true,
    )
    fun countCreatedInRangeNative(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): Long

    /** P3.6: calendar view — activities whose schedule overlaps [start, end). */
    @Query(
        """
        SELECT a FROM Activity a
        WHERE a.deletedAt IS NULL
          AND a.scheduleFrom IS NOT NULL
          AND a.scheduleFrom < :end
          AND (a.scheduleTo IS NULL OR a.scheduleTo > :start)
        ORDER BY a.scheduleFrom ASC
        """,
    )
    fun findCalendarRange(
        start: Instant,
        end: Instant,
    ): List<Activity>

    /**
     * P3.6: meeting overlap — every scheduled activity whose user/person participant
     * intersects the candidate window, excluding [excludeActivityId] (for the
     * "update existing meeting" case).
     */
    @Query(
        value = """
            SELECT DISTINCT a.* FROM activities a
            LEFT JOIN activity_participants ap ON ap.activity_id = a.id
            WHERE a.deleted_at IS NULL
              AND a.type = 'MEETING'
              AND a.schedule_from IS NOT NULL
              AND a.schedule_to IS NOT NULL
              AND a.schedule_from < :end
              AND a.schedule_to   > :start
              AND (:excludeActivityId IS NULL OR a.id <> :excludeActivityId)
              AND (
                    (cardinality(CAST(:userIds AS UUID[])) > 0   AND (a.user_id = ANY(CAST(:userIds AS UUID[]))   OR ap.participant_user_id = ANY(CAST(:userIds AS UUID[]))))
                 OR (cardinality(CAST(:personIds AS UUID[])) > 0 AND (a.person_id = ANY(CAST(:personIds AS UUID[])) OR ap.person_id = ANY(CAST(:personIds AS UUID[]))))
              )
        """,
        nativeQuery = true,
    )
    fun findOverlappingMeetings(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("userIds") userIds: Array<UUID>,
        @Param("personIds") personIds: Array<UUID>,
        @Param("excludeActivityId") excludeActivityId: UUID?,
    ): List<Activity>
}
