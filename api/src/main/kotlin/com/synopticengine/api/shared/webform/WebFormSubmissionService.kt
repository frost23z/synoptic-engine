package com.synopticengine.api.shared.webform

import com.synopticengine.api.settings.SettingsApi
import com.synopticengine.api.settings.WebFormSummary
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Phase 3 / P3.5 — request body for the public submit endpoint. */
data class WebFormSubmitPayload(
    val values: Map<String, String>,
)

/** Phase 3 / P3.5 — what the public submit endpoint returns. */
data class WebFormSubmitResult(
    val success: Boolean,
    val message: String,
    val personId: UUID? = null,
    val leadId: UUID? = null,
)

/**
 * Phase 3 / P3.5 — public web-form submission.
 *
 * Sits in the `shared` module so settings (web form metadata) and CRM
 * (person + lead creation) can both be reached without introducing a
 * settings ↔ CRM cycle. Settings exposure is via [SettingsApi]; CRM
 * writes go through [WorkflowTargetPort].
 */
@Service
class WebFormSubmissionService(
    private val settingsApi: SettingsApi,
    private val targetPort: WorkflowTargetPort,
) {
    /**
     * Submit a public web form. The request runs outside an authenticated
     * session, so we extract the tenant from the form itself and wrap entity
     * creation in [TenantContext.runAs] — the strict `@PrePersist` in
     * `BaseEntity` would otherwise reject the writes.
     */
    @Transactional
    fun submit(
        formId: UUID,
        payload: WebFormSubmitPayload,
    ): WebFormSubmitResult {
        val form =
            settingsApi.findWebFormById(formId)
                ?: throw NoSuchElementException("Web form not found: $formId")
        if (!form.isActive || form.isDeleted) {
            throw NoSuchElementException("Web form not found: $formId")
        }
        return TenantContext.runAs(form.tenantId) {
            createFromSubmission(form, payload)
        }
    }

    private fun createFromSubmission(
        form: WebFormSummary,
        payload: WebFormSubmitPayload,
    ): WebFormSubmitResult {
        val values = payload.values
        // The form ships attribute_ids, but the public payload is keyed by
        // attribute *code*. Resolve via SettingsApi.findAttributesByEntityType.
        val personAttrs = settingsApi.findAttributesByEntityType("Person").associateBy { it.id }
        val byCode: Map<String, String> =
            form.fields
                .mapNotNull { field ->
                    val attr = personAttrs[field.attributeId] ?: return@mapNotNull null
                    val raw = values[attr.code] ?: values[field.attributeId.toString()] ?: return@mapNotNull null
                    attr.code to raw
                }.toMap()

        // Minimum viable contact: at least a name or email. Try mapped attribute
        // codes first, then fall back to common keys from the payload directly —
        // a form with no attributes wired up still behaves usefully when the
        // caller posts well-known field names.
        fun pick(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { byCode[it] ?: values[it] }?.takeIf { it.isNotBlank() }

        val firstName = pick("first_name", "firstName") ?: ""
        val lastName = pick("last_name", "lastName") ?: ""
        val email = pick("email")
        val phone = pick("phone", "contact_number")
        if (firstName.isBlank() && lastName.isBlank() && email.isNullOrBlank()) {
            throw IllegalArgumentException("Submission must include first_name, last_name, or email")
        }

        val personId =
            targetPort.createPersonFromForm(
                firstName = firstName,
                lastName = lastName,
                email = email,
                phone = phone,
                jobTitle = pick("job_title", "jobTitle"),
            )

        val leadTitle = pick("lead_title", "title")
        val leadId =
            if (form.createLead) {
                targetPort.createLeadFromForm(
                    title = leadTitle ?: "Lead From Web Form",
                    description = pick("description"),
                    amount = pick("amount")?.toBigDecimalOrNull(),
                    personId = personId,
                )
            } else {
                null
            }

        return WebFormSubmitResult(
            success = true,
            message = "Form submitted successfully",
            personId = personId,
            leadId = leadId,
        )
    }
}
