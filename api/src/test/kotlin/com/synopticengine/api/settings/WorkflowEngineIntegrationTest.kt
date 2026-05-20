package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.crm.tag.repo.TagRepository
import com.synopticengine.api.settings.automation.domain.WorkflowActionRunStatus
import com.synopticengine.api.settings.automation.repo.WorkflowActionRunRepository
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.support.factories.LeadFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 3 / P3.2 — exercises the workflow action engine end-to-end:
 *  1. Create a workflow with an action.
 *  2. Fire its trigger event by creating the entity.
 *  3. Assert the action ran (tag attached) and a SUCCESS row landed in
 *     workflow_action_runs.
 */
class WorkflowEngineIntegrationTest : AbstractIntegrationTest() {
    @Autowired lateinit var actionRunRepository: WorkflowActionRunRepository

    @Autowired lateinit var tagRepository: TagRepository

    @Autowired lateinit var leadFactory: LeadFactory

    private lateinit var adminToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    @Test
    fun `add_tag action runs when its trigger event fires`() {
        val tagName = "wf-tag-${UUID.randomUUID().toString().take(8)}"

        // 1) Configure the workflow to add this tag on lead.created.
        val wfBody =
            post(
                "/api/settings/workflows",
                adminToken,
                mapOf(
                    "name" to "Add tag on lead create ${UUID.randomUUID()}",
                    "eventName" to "lead.created",
                    "conditions" to emptyList<Map<String, String>>(),
                    "actions" to listOf(mapOf("type" to "add_tag", "tagName" to tagName)),
                    "isActive" to true,
                ),
            ).bodyAsMap()!!
        val workflowId = wfBody["id"] as String

        // 2) Create a lead — should fire lead.created.
        val leadId = leadFactory.id(adminToken, title = "WF test lead")

        // Wait for the async listener to settle. The engine runs on Spring's
        // default async executor; in test it lands the run row within ms.
        waitForRun(UUID.fromString(workflowId))

        // 3) Tag should be on the lead.
        val tags = (get("/api/leads/$leadId", adminToken).bodyAsMap()!!["tags"] as List<*>)
        assertTrue(tags.any { (it as Map<*, *>)["name"] == tagName }, "expected tag $tagName on lead")

        // 4) workflow_action_runs has a SUCCESS row.
        val run =
            TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
                actionRunRepository
                    .findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        entityType = "Lead",
                        entityId = leadId,
                        pageable = PageRequest.of(0, 5),
                    ).content
                    .firstOrNull { it.workflowId == UUID.fromString(workflowId) }
            }
        assertNotNull(run, "no workflow_action_runs row for workflow $workflowId / lead $leadId")
        assertEquals(WorkflowActionRunStatus.SUCCESS, run.status)
        assertEquals("add_tag", run.actionType)
    }

    @Test
    fun `workflow condition gating produces a SKIPPED run`() {
        val wfBody =
            post(
                "/api/settings/workflows",
                adminToken,
                mapOf(
                    "name" to "Conditional WF ${UUID.randomUUID()}",
                    "eventName" to "lead.created",
                    "conditions" to
                        listOf(
                            mapOf("attribute" to "status", "operator" to "equals", "value" to "won"),
                        ),
                    "actions" to listOf(mapOf("type" to "add_tag", "tagName" to "won-only")),
                    "isActive" to true,
                ),
            ).bodyAsMap()!!
        val workflowId = UUID.fromString(wfBody["id"] as String)

        // Lead is created OPEN — condition (status=won) should fail.
        val leadId = leadFactory.id(adminToken, title = "Conditional lead")

        waitForRun(workflowId)

        val run =
            TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
                actionRunRepository
                    .findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        entityType = "Lead",
                        entityId = leadId,
                        pageable = PageRequest.of(0, 10),
                    ).content
                    .firstOrNull { it.workflowId == workflowId }
            }
        assertNotNull(run)
        assertEquals(WorkflowActionRunStatus.SKIPPED, run.status)
    }

    @Test
    fun `unknown action type lands a SKIPPED run, not a crash`() {
        val wfBody =
            post(
                "/api/settings/workflows",
                adminToken,
                mapOf(
                    "name" to "Bogus action WF ${UUID.randomUUID()}",
                    "eventName" to "lead.created",
                    "actions" to listOf(mapOf("type" to "definitely_not_a_real_action")),
                    "isActive" to true,
                ),
            ).bodyAsMap()!!
        val workflowId = UUID.fromString(wfBody["id"] as String)

        val leadId = leadFactory.id(adminToken, title = "Bogus action lead")

        waitForRun(workflowId)

        val run =
            TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
                actionRunRepository
                    .findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        entityType = "Lead",
                        entityId = leadId,
                        pageable = PageRequest.of(0, 10),
                    ).content
                    .firstOrNull { it.workflowId == workflowId }
            }
        assertNotNull(run)
        assertEquals(WorkflowActionRunStatus.SKIPPED, run.status)
    }

    @Test
    fun `runs endpoint paginates the workflow's action runs`() {
        val wfId =
            post(
                "/api/settings/workflows",
                adminToken,
                mapOf(
                    "name" to "Runs endpoint WF ${UUID.randomUUID()}",
                    "eventName" to "lead.created",
                    "actions" to listOf(mapOf("type" to "add_tag", "tagName" to "runs-test")),
                    "isActive" to true,
                ),
            ).bodyAsMap()!!["id"] as String

        leadFactory.create(adminToken, title = "Runs lead")
        waitForRun(UUID.fromString(wfId))

        val result = get("/api/settings/workflows/$wfId/runs", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val body = result.bodyAsMap()!!
        val content = body["content"] as List<*>
        assertTrue(content.isNotEmpty())
    }

    /**
     * The engine handler is `@Async`. Poll for up to ~5s for a run to appear.
     * Avoids the flake of an ill-placed Thread.sleep.
     */
    private fun waitForRun(workflowId: UUID) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val found =
                TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
                    actionRunRepository
                        .findAllByWorkflowIdOrderByCreatedAtDesc(workflowId, PageRequest.of(0, 1))
                        .totalElements > 0
                }
            if (found) return
            Thread.sleep(100)
        }
    }
}
