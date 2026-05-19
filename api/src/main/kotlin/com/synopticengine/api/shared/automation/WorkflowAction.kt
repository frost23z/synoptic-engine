package com.synopticengine.api.shared.automation

/**
 * Strategy contract for one workflow action type.
 *
 * Implementations are registered as Spring beans; [WorkflowEngine] resolves
 * one per `action.type` value and invokes [execute]. A successful return
 * lands a `SUCCESS` row in workflow_action_runs; thrown exceptions land
 * `FAILED` and the error message.
 */
interface WorkflowAction {
    /** Matches the `type` field in the workflow's actions JSON. */
    val type: String

    /**
     * Execute the action against the current context. Return any payload
     * worth persisting on the run row (e.g. `{ "tagId": <uuid> }`); use
     * `emptyMap()` if nothing needs recording.
     *
     * Throw to mark the run as FAILED.
     */
    fun execute(ctx: WorkflowActionContext): Map<String, Any?>
}
