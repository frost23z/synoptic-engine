package com.synopticengine.api.crm.contact.domain

/**
 * One row of a Person's `emails` / `contact_numbers` JSON array.
 * Mirrors Krayin's `[{value, label}]` shape so existing form payloads round-trip.
 */
data class ContactEntry(
    val value: String,
    val label: String = "primary",
)
