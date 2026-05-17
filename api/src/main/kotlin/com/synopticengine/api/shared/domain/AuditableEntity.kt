package com.synopticengine.api.shared.domain

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.LastModifiedBy
import java.util.UUID

@MappedSuperclass
abstract class AuditableEntity : BaseEntity() {
    @CreatedBy
    @Column(updatable = false, columnDefinition = "uuid")
    var createdBy: UUID? = null
        protected set

    @LastModifiedBy
    @Column(columnDefinition = "uuid")
    var updatedBy: UUID? = null
        protected set
}
