package com.synopticengine.api.shared.email

private val TOKEN_REGEX = Regex("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}")

fun interpolateTemplate(
    template: String,
    context: Map<String, String>,
): String =
    TOKEN_REGEX.replace(template) { match ->
        context[match.groupValues[1]] ?: ""
    }
