package com.synopticengine.api.crm.lead.service

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.synopticengine.api.crm.lead.web.LeadResponse
import org.apache.tika.Tika
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate

@Service
class AiLeadService(
    private val leadService: LeadService,
    private val objectMapper: ObjectMapper,
) {
    // Lazy so startup doesn't fail when ANTHROPIC_API_KEY is absent (e.g. test environments).
    private val client: AnthropicClient by lazy { AnthropicOkHttpClient.fromEnv() }
    private val tika = Tika()

    fun createFromFile(
        file: MultipartFile,
        hints: String?,
    ): LeadResponse {
        val rawText = tika.parseToString(file.inputStream).take(MAX_TIKA_CHARS)
        val prompt = buildPrompt(rawText, hints)

        val params =
            MessageCreateParams
                .builder()
                .model("claude-opus-4-8")
                .maxTokens(4096L)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .systemOfTextBlockParams(
                    listOf(
                        TextBlockParam
                            .builder()
                            .text(SYSTEM_PROMPT)
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build(),
                    ),
                ).addUserMessage(prompt)
                .build()

        val message = client.messages().create(params)
        val jsonText =
            message
                .content()
                .firstNotNullOfOrNull { it.text().orElse(null)?.text() }
                ?: throw IllegalStateException("Claude returned no text for AI lead extraction")

        val fields = extractFields(jsonText)
        return leadService.create(
            title = fields.title?.takeIf { it.isNotBlank() } ?: deriveTitle(file),
            description = fields.description,
            amount = fields.amount?.let { runCatching { BigDecimal(it) }.getOrNull() },
            expectedCloseDate = fields.expectedCloseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            pipelineId = null,
            stageId = null,
            personId = null,
            organizationId = null,
            leadSourceId = null,
            leadTypeId = null,
            userId = null,
        )
    }

    private fun buildPrompt(
        rawText: String,
        hints: String?,
    ): String =
        buildString {
            append("Extract lead information from the following document:\n\n")
            append(rawText)
            if (!hints.isNullOrBlank()) {
                append("\n\nAdditional context: $hints")
            }
        }

    private fun deriveTitle(file: MultipartFile): String =
        file.originalFilename
            ?.substringBeforeLast(".")
            ?.replace(Regex("[_\\-]+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Untitled Lead"

    private fun extractFields(text: String): ExtractedFields {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return ExtractedFields()
        return runCatching {
            objectMapper.readValue(text.substring(start, end + 1), ExtractedFields::class.java)
        }.getOrElse { ExtractedFields() }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ExtractedFields(
        val title: String? = null,
        val description: String? = null,
        val amount: String? = null,
        val expectedCloseDate: String? = null,
    )

    companion object {
        private const val MAX_TIKA_CHARS = 50_000

        private val SYSTEM_PROMPT =
            """
You are a CRM lead-extraction assistant. Given a document, extract lead information and respond with ONLY a JSON object.

Required JSON shape (use null for any field that cannot be determined):
{
  "title": "lead or opportunity name (company, project, or subject)",
  "description": "brief description of the sales opportunity",
  "amount": "deal value as a plain decimal number string without symbols, e.g. 50000",
  "expectedCloseDate": "expected close date in YYYY-MM-DD format"
}

Rules:
- Respond with ONLY the JSON object — no preamble, no explanation, no markdown fences.
- title must be non-null; derive it from the document subject if no explicit title exists.
- amount must be a plain decimal number or null (no currency symbols, commas, or spaces).
- expectedCloseDate must be ISO date YYYY-MM-DD or null.
            """.trimIndent()
    }
}
