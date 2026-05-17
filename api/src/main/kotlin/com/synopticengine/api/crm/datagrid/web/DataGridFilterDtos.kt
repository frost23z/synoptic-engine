package com.synopticengine.api.crm.datagrid.web

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class SaveFilterRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val src: String,
    val applied: Map<String, Any> = emptyMap(),
)

data class UpdateFilterRequest(
    @field:NotBlank val name: String,
    val applied: Map<String, Any> = emptyMap(),
)

data class DataGridFilterResponse(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val src: String,
    val applied: Map<String, Any>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)
