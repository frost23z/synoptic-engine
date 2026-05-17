package com.synopticengine.api.settings.webform.repo

import com.synopticengine.api.settings.webform.domain.WebForm
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface WebFormRepository : JpaRepository<WebForm, UUID> {
    @Query("SELECT w FROM WebForm w LEFT JOIN FETCH w.fields WHERE w.id = :id")
    fun findByIdWithFields(id: UUID): WebForm?

    fun findAllByIsActiveTrue(): List<WebForm>
}
