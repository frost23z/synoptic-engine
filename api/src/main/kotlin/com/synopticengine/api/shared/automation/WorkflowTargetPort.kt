package com.synopticengine.api.shared.automation

import java.util.UUID

/**
 * Port the workflow action engine uses to mutate domain records. The
 * implementation lives inside whichever module owns the target (today: CRM)
 * but the actions consume only this interface so they stay module-neutral.
 *
 * Each method returns the affected entity id (or null when the target
 * doesn't exist). Actions raise on missing targets — the engine catches
 * and records a FAILED run.
 */
interface WorkflowTargetPort {
    /** Set a single allow-listed field on a lead. */
    fun updateLeadField(
        leadId: UUID,
        field: String,
        value: String?,
    ): UUID?

    /** Set a single allow-listed field on a person. */
    fun updatePersonField(
        personId: UUID,
        field: String,
        value: String?,
    ): UUID?

    /** Attach a tag (by id or name; created if name and missing) to a lead. */
    fun ensureLeadTag(
        leadId: UUID,
        tagId: UUID?,
        tagName: String?,
    ): UUID?

    /** Attach a tag to a person. */
    fun ensurePersonTag(
        personId: UUID,
        tagId: UUID?,
        tagName: String?,
    ): UUID?

    /** Create a NOTE activity attached to the given entity. */
    fun createNoteActivity(
        entityType: String,
        entityId: UUID,
        title: String,
        comment: String?,
    ): UUID

    /** Look up the person linked to a lead, with the person's primary email. */
    fun findLeadPersonAndEmail(leadId: UUID): Pair<UUID, String?>?

    /** Look up a person's primary email. */
    fun findPersonEmail(personId: UUID): String?

    /** Look up the user id assigned to a lead. */
    fun findLeadOwnerId(leadId: UUID): UUID?

    /** Assign lead owner directly. */
    fun assignLeadUser(
        leadId: UUID,
        userId: UUID,
    ): UUID?

    /** Assign lead stage (and auto-align pipeline). */
    fun assignLeadStage(
        leadId: UUID,
        stageId: UUID,
    ): UUID?

    /** Assign lead by group (first active group member). */
    fun assignLeadGroup(
        leadId: UUID,
        groupId: UUID,
    ): UUID?

    // ── Web form submission helpers (P3.5) ─────────────────────────────────

    /**
     * Create a person from a web-form payload. Returns the person id.
     * The web form layer can't reach into CRM internals directly, so it
     * funnels through this port.
     */
    fun createPersonFromForm(
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        jobTitle: String?,
    ): UUID

    /**
     * Create a lead linked to a freshly-created person.
     * Returns `null` if the tenant has no default pipeline configured.
     */
    fun createLeadFromForm(
        title: String,
        description: String?,
        amount: java.math.BigDecimal?,
        personId: UUID?,
    ): UUID?
}
