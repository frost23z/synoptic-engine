package com.synopticengine.api.sharing.service

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

@Component
class TenantSharePolicyFilterEvaluator(
    private val objectMapper: ObjectMapper,
) {
    fun validate(filterJson: String?) {
        if (filterJson.isNullOrBlank()) return
        val filter = parseJson(filterJson, "Invalid filterJson")
        validateShape(filter)
    }

    fun matches(
        filterJson: String?,
        resourceJson: String,
    ): Boolean {
        if (filterJson.isNullOrBlank()) return true
        val filter = parseJson(filterJson, "Invalid filterJson")
        validateShape(filter)
        val resource = parseJson(resourceJson, "Invalid resource row payload")
        return evaluate(filter, resource)
    }

    private fun validateShape(node: JsonNode) {
        if (node.isObject) {
            val hasRule = node.hasNonNull("field")
            val hasAny = node.has("any")
            val hasAll = node.has("all")
            val hasNot = node.has("not")
            if ((hasRule.asIntValue() + hasAny.asIntValue() + hasAll.asIntValue() + hasNot.asIntValue()) > 1) {
                throw IllegalArgumentException("filterJson node cannot mix rule keys with boolean operator keys")
            }
            if (hasAny) requireArray(node.get("any"), "filterJson.any must be an array")
            if (hasAll) requireArray(node.get("all"), "filterJson.all must be an array")
            if (hasNot) validateShape(node.get("not"))
            if (hasRule) {
                val field =
                    node
                        .get("field")
                        ?.asText()
                        ?.trim()
                        .orEmpty()
                if (field.isBlank()) throw IllegalArgumentException("filterJson rule field is required")
                val op =
                    node
                        .get("op")
                        ?.asText()
                        ?.lowercase()
                        ?.ifBlank { "eq" } ?: "eq"
                if (op !in SUPPORTED_OPS) throw IllegalArgumentException("Unsupported filterJson operator: $op")
            }
            node.get("any")?.forEach { validateShape(it) }
            node.get("all")?.forEach { validateShape(it) }
            return
        }
        if (node.isArray) {
            node.forEach { validateShape(it) }
            return
        }
        throw IllegalArgumentException("filterJson must be an object or array")
    }

    private fun evaluate(
        filter: JsonNode,
        resource: JsonNode,
    ): Boolean {
        if (filter.isArray) return filter.all { evaluate(it, resource) }
        if (!filter.isObject) return false

        when {
            filter.has("all") -> return filter.get("all").all { evaluate(it, resource) }
            filter.has("any") -> return filter.get("any").any { evaluate(it, resource) }
            filter.has("not") -> return !evaluate(filter.get("not"), resource)
            filter.has("field") -> return evaluateRule(filter, resource)
        }

        // Shorthand: {"status":"open","title":"Acme"} = conjunction of eq rules.
        @Suppress("UNCHECKED_CAST")
        val shorthand = objectMapper.readValue(filter.toString(), Map::class.java) as Map<String, Any?>
        return shorthand.all { (field, expected) ->
            compare(getPath(resource, field), objectMapper.valueToTree(expected), "eq")
        }
    }

    private fun evaluateRule(
        rule: JsonNode,
        resource: JsonNode,
    ): Boolean {
        val field = rule.get("field").asText()
        val op =
            rule
                .get("op")
                ?.asText()
                ?.lowercase()
                ?.ifBlank { "eq" } ?: "eq"
        val actual = getPath(resource, field)
        val expected = rule.get("value")
        return compare(actual, expected, op)
    }

    private fun compare(
        actual: JsonNode?,
        expected: JsonNode?,
        op: String,
    ): Boolean =
        when (op) {
            "exists" -> actual != null && !actual.isNull
            "eq" -> nodeText(actual) == nodeText(expected)
            "neq" -> nodeText(actual) != nodeText(expected)
            "contains" -> nodeText(actual).contains(nodeText(expected), ignoreCase = true)
            "starts_with" -> nodeText(actual).startsWith(nodeText(expected), ignoreCase = true)
            "ends_with" -> nodeText(actual).endsWith(nodeText(expected), ignoreCase = true)
            "in" -> expected?.takeIf { it.isArray }?.any { nodeText(it) == nodeText(actual) } == true
            "not_in" -> expected?.takeIf { it.isArray }?.none { nodeText(it) == nodeText(actual) } ?: true
            "gt" -> nodeDecimal(actual) > nodeDecimal(expected)
            "gte" -> nodeDecimal(actual) >= nodeDecimal(expected)
            "lt" -> nodeDecimal(actual) < nodeDecimal(expected)
            "lte" -> nodeDecimal(actual) <= nodeDecimal(expected)
            else -> false
        }

    private fun parseJson(
        value: String,
        error: String,
    ): JsonNode =
        runCatching { objectMapper.readTree(value) }.getOrElse {
            throw IllegalArgumentException(error)
        }

    private fun getPath(
        node: JsonNode,
        field: String,
    ): JsonNode? {
        var current: JsonNode? = node
        for (segment in field.split('.')) {
            if (current == null || !current.isObject) return null
            current = current.get(segment)
        }
        return current
    }

    private fun nodeText(node: JsonNode?): String =
        when {
            node == null || node.isNull -> ""
            node.isTextual -> node.asText()
            node.isNumber || node.isBoolean -> node.asText()
            else -> node.toString()
        }

    private fun nodeDecimal(node: JsonNode?): BigDecimal =
        node
            ?.takeIf { it.isNumber || it.isTextual }
            ?.asText()
            ?.toBigDecimalOrNull()
            ?: BigDecimal.ZERO

    private fun Boolean.asIntValue(): Int = if (this) 1 else 0

    private fun requireArray(
        node: JsonNode?,
        error: String,
    ) {
        if (node == null || !node.isArray) throw IllegalArgumentException(error)
    }

    private companion object {
        val SUPPORTED_OPS =
            setOf(
                "eq",
                "neq",
                "in",
                "not_in",
                "contains",
                "starts_with",
                "ends_with",
                "gt",
                "gte",
                "lt",
                "lte",
                "exists",
            )
    }
}
