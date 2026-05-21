package com.synopticengine.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.assertEquals

/**
 * Phase 4 P1-5: every `@PreAuthorize` key should have at least one negative-path test
 * proving the endpoint returns 403 to a caller that lacks the permission. The audit
 * flagged 14+ keys (mostly the cross-tenant trust graph plus admin-only configuration)
 * with no such test today.
 *
 * Setup: provision a user with a role that has zero permission edges, log them in,
 * and hit each protected endpoint. `@PreAuthorize` fires before any tenant or
 * existence check, so the response is unambiguously 403 — no matter what UUIDs we
 * stuff in the URL or what shape the body has.
 *
 * Replaces what would have been 14 separate hand-written mini-tests.
 */
class PermissionDenialCoverageIntegrationTest : AbstractIntegrationTest() {
    private lateinit var noPermsToken: String

    @BeforeEach
    fun setup() {
        val admin = adminToken()
        val roleName = "NOPERMS_${UUID.randomUUID().toString().take(6)}"
        // A role with an empty permissions list is allowed by RoleService;
        // every @PreAuthorize check on the resulting user returns 403.
        post("/api/roles", admin, mapOf("name" to roleName, "permissions" to emptyList<String>()))
        noPermsToken = tokenFor(setOf(roleName))
    }

    @ParameterizedTest(name = "[{index}] {0} {1} → 403 when caller lacks {2}")
    @MethodSource("uncoveredEndpoints")
    fun `endpoint rejects a caller missing the required permission`(
        httpMethod: String,
        url: String,
        @Suppress("UNUSED_PARAMETER") missingKey: String,
        body: Map<String, Any?>?,
    ) {
        val result =
            when (httpMethod) {
                "GET" -> get(url, noPermsToken)
                "POST" -> post(url, noPermsToken, body)
                "PUT" -> put(url, noPermsToken, body)
                "PATCH" -> patch(url, noPermsToken, body)
                "DELETE" -> delete(url, noPermsToken)
                else -> error("Unsupported method: $httpMethod")
            }
        assertEquals(403, result.status(), "Expected 403, got ${result.status()}: ${result.response.contentAsString}")
    }

    companion object {
        // Path params are arbitrary — the @PreAuthorize check fires before existence
        // checks, so 403 is returned regardless of whether the id resolves. For POST/PUT
        // we still send a minimum-shape body because Spring may bind the body before
        // running the security advice.
        private val ANY_ID = "00000000-0000-0000-0000-000000000000"

        @JvmStatic
        fun uncoveredEndpoints(): Stream<Arguments> =
            Stream.of(
                // ── Identity: tenants ──────────────────────────────────────
                Arguments.of("GET", "/api/tenants", "tenants.view", null),
                Arguments.of("GET", "/api/tenants/$ANY_ID", "tenants.view", null),
                Arguments.of(
                    "POST",
                    "/api/tenants",
                    "tenants.manage",
                    mapOf(
                        "name" to "X",
                        "slug" to "x",
                        "adminEmail" to "a@b.test",
                        "adminPassword" to "Password123!",
                    ),
                ),
                // ── CRM: reports + pipelines + tags ────────────────────────
                Arguments.of("GET", "/api/dashboard/stats?type=over-all", "reports.view", null),
                Arguments.of("POST", "/api/pipelines", "pipelines.create", mapOf("name" to "x")),
                Arguments.of("POST", "/api/tags", "tags.create", mapOf("name" to "x")),
                // ── Settings: imports ──────────────────────────────────────
                Arguments.of("GET", "/api/settings/imports", "imports.view", null),
                Arguments.of("GET", "/api/settings/imports/$ANY_ID", "imports.view", null),
                Arguments.of("POST", "/api/settings/imports/$ANY_ID/start", "imports.edit", null),
                // ── Settings: automations ──────────────────────────────────
                Arguments.of("GET", "/api/settings/workflows", "automations.view", null),
                Arguments.of(
                    "POST",
                    "/api/settings/workflows",
                    "automations.create",
                    mapOf("name" to "x", "eventName" to "lead.created"),
                ),
                Arguments.of(
                    "PUT",
                    "/api/settings/workflows/$ANY_ID",
                    "automations.edit",
                    mapOf("name" to "x", "eventName" to "lead.created"),
                ),
                // ── Settings: attributes / email templates / web forms ─────
                Arguments.of("GET", "/api/settings/attributes", "attributes.view", null),
                Arguments.of(
                    "POST",
                    "/api/settings/attributes",
                    "attributes.create",
                    mapOf("code" to "x", "adminName" to "x", "type" to "TEXT", "entityType" to "Lead"),
                ),
                Arguments.of(
                    "PUT",
                    "/api/settings/attributes/$ANY_ID",
                    "attributes.edit",
                    mapOf("adminName" to "x", "type" to "TEXT", "sortOrder" to 1),
                ),
                Arguments.of("DELETE", "/api/settings/attributes/$ANY_ID", "attributes.delete", null),
                Arguments.of("GET", "/api/settings/email-templates", "email-templates.view", null),
                Arguments.of(
                    "POST",
                    "/api/settings/email-templates",
                    "email-templates.create",
                    mapOf("name" to "x", "subject" to "x", "content" to "<p>x</p>"),
                ),
                Arguments.of(
                    "PUT",
                    "/api/settings/email-templates/$ANY_ID",
                    "email-templates.edit",
                    mapOf("name" to "x", "subject" to "x", "content" to "<p>x</p>"),
                ),
                Arguments.of("DELETE", "/api/settings/email-templates/$ANY_ID", "email-templates.delete", null),
                Arguments.of("GET", "/api/settings/web-forms", "web-forms.view", null),
                Arguments.of(
                    "POST",
                    "/api/settings/web-forms",
                    "web-forms.create",
                    mapOf("title" to "x", "isActive" to true),
                ),
                Arguments.of(
                    "PUT",
                    "/api/settings/web-forms/$ANY_ID",
                    "web-forms.edit",
                    mapOf("title" to "x", "isActive" to true),
                ),
                Arguments.of("DELETE", "/api/settings/web-forms/$ANY_ID", "web-forms.delete", null),
                // ── Sharing: relationships, share policies, record shares ──
                Arguments.of("GET", "/api/relationships", "relationships.view", null),
                Arguments.of(
                    "POST",
                    "/api/relationships",
                    "relationships.manage",
                    mapOf("targetTenantId" to ANY_ID, "type" to "PARTNER"),
                ),
                Arguments.of(
                    "GET",
                    "/api/relationships/$ANY_ID/policies",
                    "share-policies.view",
                    null,
                ),
                Arguments.of(
                    "POST",
                    "/api/relationships/$ANY_ID/policies",
                    "share-policies.manage",
                    mapOf("resourceType" to "leads", "accessLevel" to "READ"),
                ),
                Arguments.of(
                    "POST",
                    "/api/records/share",
                    "records.share",
                    mapOf(
                        "consumerTenantId" to ANY_ID,
                        "resourceType" to "leads",
                        "resourceId" to ANY_ID,
                        "accessLevel" to "READ",
                    ),
                ),
            )
    }
}
