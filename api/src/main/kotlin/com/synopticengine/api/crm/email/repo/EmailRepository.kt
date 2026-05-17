package com.synopticengine.api.crm.email.repo

import com.synopticengine.api.crm.email.domain.Email
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface EmailRepository : JpaRepository<Email, UUID> {
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM emails WHERE folders @> jsonb_build_array(cast(:folder as text))",
        countQuery = "SELECT count(*) FROM emails WHERE folders @> jsonb_build_array(cast(:folder as text))",
    )
    fun findByFolder(
        folder: String,
        pageable: Pageable,
    ): Page<Email>
}
