package com.synopticengine.api.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "login_history")
class LoginHistory {
    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    var id: UUID = UUID.randomUUID()

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    var userId: UUID = UUID.randomUUID()

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    var tenantId: UUID = UUID.randomUUID()

    @Column(name = "client_ip", length = 45)
    var clientIp: String? = null

    @Column(name = "logged_in_at", nullable = false)
    var loggedInAt: Instant = Instant.now()
}
