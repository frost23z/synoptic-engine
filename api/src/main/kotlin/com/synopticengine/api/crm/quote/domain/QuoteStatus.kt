package com.synopticengine.api.crm.quote.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component
import org.springframework.core.convert.converter.Converter as SpringConverter

enum class QuoteStatus(
    val value: String,
) {
    DRAFT("draft"),
    SENT("sent"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    EXPIRED("expired"),
    ;

    companion object {
        fun fromValue(v: String): QuoteStatus {
            val normalized = v.lowercase()
            if (normalized == "declined") return REJECTED
            return entries.first { it.value == normalized }
        }
    }
}

@Converter
class QuoteStatusConverter : AttributeConverter<QuoteStatus, String> {
    override fun convertToDatabaseColumn(attr: QuoteStatus?): String? = attr?.value

    override fun convertToEntityAttribute(col: String?): QuoteStatus? = col?.let { QuoteStatus.fromValue(it) }
}

@Component
class QuoteStatusStringConverter : SpringConverter<String, QuoteStatus> {
    override fun convert(source: String): QuoteStatus = QuoteStatus.fromValue(source)
}
