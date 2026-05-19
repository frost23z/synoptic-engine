package com.synopticengine.api.settings.automation.service

import org.springframework.stereotype.Component

/**
 * Evaluates a list of conditions against a [DomainEvent]'s payload using a
 * top-level AND or OR. Each condition is a triple `{attribute, operator, value}`:
 *
 * | operator              | meaning                                                       |
 * |-----------------------|---------------------------------------------------------------|
 * | `equals`, `=`         | string-equal (case-sensitive)                                 |
 * | `not_equals`, `!=`    | string-not-equal                                              |
 * | `contains`, `like`    | substring, case-insensitive                                   |
 * | `not_contains`, `!like` | negation of `like`                                          |
 * | `starts_with`         | starts-with, case-insensitive                                 |
 * | `ends_with`           | ends-with, case-insensitive                                   |
 * | `>` / `<` / `>=` / `<=` | numeric compare; non-numeric on either side ⇒ false        |
 * | `in`                  | comma-separated value list contains actual                    |
 * | `not_in`              | negation of `in`                                              |
 * | `is_empty`            | actual is null or blank                                       |
 * | `is_not_empty`        | actual is non-blank                                           |
 */
@Component
class WorkflowConditionEvaluator {
    fun evaluate(
        conditions: List<Map<String, Any?>>,
        conditionType: String,
        payload: Map<String, Any?>,
    ): Boolean {
        if (conditions.isEmpty()) return true
        val results = conditions.map { evaluateOne(it, payload) }
        return when (conditionType.lowercase()) {
            "or" -> results.any { it }
            else -> results.all { it }
        }
    }

    private fun evaluateOne(
        condition: Map<String, Any?>,
        payload: Map<String, Any?>,
    ): Boolean {
        val attribute = (condition["attribute"] ?: condition["field"])?.toString() ?: return true
        val operator = condition["operator"]?.toString()?.lowercase() ?: "equals"
        val expected = condition["value"]?.toString() ?: ""
        val actual = payload[attribute]?.toString().orEmpty()
        return when (operator) {
            "equals", "=" -> actual == expected
            "not_equals", "!=" -> actual != expected
            "contains", "like" -> actual.contains(expected, ignoreCase = true)
            "not_contains", "!like" -> !actual.contains(expected, ignoreCase = true)
            "starts_with" -> actual.startsWith(expected, ignoreCase = true)
            "ends_with" -> actual.endsWith(expected, ignoreCase = true)
            ">" -> numericCompare(actual, expected) { a, b -> a > b }
            "<" -> numericCompare(actual, expected) { a, b -> a < b }
            ">=" -> numericCompare(actual, expected) { a, b -> a >= b }
            "<=" -> numericCompare(actual, expected) { a, b -> a <= b }
            "in" -> expected.split(",").map { it.trim() }.contains(actual)
            "not_in" -> !expected.split(",").map { it.trim() }.contains(actual)
            "is_empty" -> actual.isBlank()
            "is_not_empty" -> actual.isNotBlank()
            else -> false
        }
    }

    private inline fun numericCompare(
        a: String,
        b: String,
        cmp: (java.math.BigDecimal, java.math.BigDecimal) -> Boolean,
    ): Boolean {
        val aN = a.toBigDecimalOrNull() ?: return false
        val bN = b.toBigDecimalOrNull() ?: return false
        return cmp(aN, bN)
    }
}
