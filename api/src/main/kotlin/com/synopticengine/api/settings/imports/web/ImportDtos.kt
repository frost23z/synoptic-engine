package com.synopticengine.api.settings.imports.web

import com.synopticengine.api.settings.imports.domain.ImportStatus
import java.time.Instant
import java.util.UUID

data class DataImportResponse(
    val id: UUID,
    val name: String,
    val entityType: String,
    val status: ImportStatus,
    val errorCount: Int,
    val successCount: Int,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class DataImportStatsResponse(
    val id: UUID,
    val status: ImportStatus,
    val errorCount: Int,
    val successCount: Int,
    val errors: List<Map<String, String>>?,
)
