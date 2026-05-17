package com.synopticengine.api.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_password_resets")
class PasswordReset {
    @Id
    @Column(nullable = false, length = 255)
    var email: String = ""

    @Column(nullable = false, length = 255)
    var token: String = ""

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    fun isExpired(ttlMinutes: Long = 60): Boolean = createdAt.plusSeconds(ttlMinutes * 60).isBefore(Instant.now())
}
