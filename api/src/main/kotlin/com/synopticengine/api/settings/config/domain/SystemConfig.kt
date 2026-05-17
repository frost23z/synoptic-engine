package com.synopticengine.api.settings.config.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "system_configs")
class SystemConfig {
    @Id
    @Column(nullable = false)
    var code: String = ""

    @Column(columnDefinition = "TEXT")
    var value: String? = null

    @Column(nullable = false)
    var groupName: String = "general"

    @Column(nullable = false)
    var label: String = ""

    @Column(nullable = false)
    var type: String = "text"

    @Column(nullable = false)
    var isSecret: Boolean = false

    @Column(nullable = false)
    var sortOrder: Int = 0

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}
