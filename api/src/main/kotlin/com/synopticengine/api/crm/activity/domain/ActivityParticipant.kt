package com.synopticengine.api.crm.activity.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.util.UUID

/**
 * One participant on an Activity — either a [userId] or a [personId], never both.
 * The DB constraint `chk_activity_participants_one_target` enforces the exclusive-or,
 * mirroring Krayin's polymorphic participant column.
 */
@Entity
@Table(name = "activity_participants")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class ActivityParticipant : BaseEntity() {
    @Column(name = "activity_id", nullable = false)
    var activityId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    @Column(name = "participant_user_id")
    var userId: UUID? = null

    @Column(name = "person_id")
    var personId: UUID? = null
}
