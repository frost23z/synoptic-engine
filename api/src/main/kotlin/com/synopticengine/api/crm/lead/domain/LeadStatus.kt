package com.synopticengine.api.crm.lead.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

enum class LeadStatus(
    val value: String,
) {
    OPEN("open"),
    WON("won"),
    LOST("lost"),
    ABANDONED("abandoned"),
    ;

    companion object {
        fun fromValue(v: String): LeadStatus = entries.first { it.value == v.lowercase() }
    }
}

@Converter
class LeadStatusConverter : AttributeConverter<LeadStatus, String> {
    override fun convertToDatabaseColumn(attr: LeadStatus?): String? = attr?.value

    override fun convertToEntityAttribute(col: String?): LeadStatus? = col?.let { LeadStatus.fromValue(it) }
}
