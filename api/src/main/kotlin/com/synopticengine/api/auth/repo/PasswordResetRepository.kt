package com.synopticengine.api.auth.repo

import com.synopticengine.api.auth.domain.PasswordReset
import org.springframework.data.jpa.repository.JpaRepository

interface PasswordResetRepository : JpaRepository<PasswordReset, String> {
    fun deleteByEmail(email: String)
}
