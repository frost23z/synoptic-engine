package com.synopticengine.api.shared.automation

import com.synopticengine.api.shared.DomainEvent

/**
 * Everything an action strategy needs to know about the firing event.
 * Pure data — no Spring or DB types — so it serialises cleanly into the
 * workflow_action_runs.payload column.
 */
data class WorkflowActionContext(
    val event: DomainEvent,
    /** Decoded from the action JSON. */
    val action: Map<String, Any?>,
)
