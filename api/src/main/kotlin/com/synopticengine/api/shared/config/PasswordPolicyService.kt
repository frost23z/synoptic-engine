package com.synopticengine.api.shared.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class PasswordPolicyService(
    @Value("\${synoptic.auth.password-policy.min-length:8}") private val minLength: Int,
    @Value("\${synoptic.auth.password-policy.require-uppercase:false}") private val requireUppercase: Boolean,
    @Value("\${synoptic.auth.password-policy.require-digit:false}") private val requireDigit: Boolean,
    @Value("\${synoptic.auth.password-policy.require-special:false}") private val requireSpecial: Boolean,
) {
    fun validate(password: String) {
        val errors = mutableListOf<String>()
        if (password.length < minLength) errors.add("at least $minLength characters")
        if (requireUppercase && password.none { it.isUpperCase() }) errors.add("at least one uppercase letter")
        if (requireDigit && password.none { it.isDigit() }) errors.add("at least one digit")
        if (requireSpecial && password.none { !it.isLetterOrDigit() }) errors.add("at least one special character")
        if (errors.isNotEmpty()) throw IllegalArgumentException("Password must contain ${errors.joinToString(", ")}")
    }
}
