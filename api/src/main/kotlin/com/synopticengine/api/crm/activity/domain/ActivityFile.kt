package com.synopticengine.api.crm.activity.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.util.UUID

@Entity
@Table(name = "activity_files")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class ActivityFile : BaseEntity() {
    @Column(name = "activity_id", nullable = false)
    var activityId: UUID = UUID.randomUUID()

    @Column(nullable = false, length = 500)
    var name: String = ""

    @Column(nullable = false, length = 1000)
    var path: String = ""

    @Column
    var size: Long? = null

    @Column(name = "content_type")
    var contentType: String? = null
}
