package com.synopticengine.api.shared

import java.util.UUID

data class DomainEvent(
    val eventName: String,
    val entityType: String,
    val entityId: UUID,
    val payload: Map<String, Any?> = emptyMap(),
)
