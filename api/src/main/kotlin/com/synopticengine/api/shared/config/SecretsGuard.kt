package com.synopticengine.api.shared.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Startup-time guard against shipping the application.yaml defaults to production
 * (H11). The JWT_SECRET and SYNOPTIC_ADMIN_PASSWORD properties have built-in
 * dev-friendly fallbacks; without this guard a deployment that forgot to set the
 * environment variables would boot silently with a publicly-known secret and a
 * trivial admin login.
 *
 * Behaviour:
 *  - On the `local`, `test`, `dev` profiles defaults are permitted; we WARN once so
 *    the operator sees the call-out in logs.
 *  - Empty-profile deployments are treated as non-dev by default to avoid silently
 *    booting production with fallback secrets. Opt into the old behaviour with
 *    `synoptic.security.empty-profile-is-dev=true` if needed for local runs.
 *  - On any other profile (notably `prod`), defaults trip a startup failure.
 *
 * Override the recognised-as-dev profile list with
 * `synoptic.security.dev-profiles=local,test,dev` if your naming differs.
 */
@Component
class SecretsGuard(
    private val environment: Environment,
    @Value("\${jwt.secret}") private val jwtSecret: String,
    @Value("\${synoptic.admin.password}") private val adminPassword: String,
    @Value("\${synoptic.security.dev-profiles:local,test,dev}") private val devProfilesCsv: String,
    @Value("\${synoptic.security.empty-profile-is-dev:false}") private val emptyProfileIsDev: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun verify() {
        val activeProfiles = environment.activeProfiles.toSet()
        val devProfiles =
            devProfilesCsv
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        val isDevDeployment = (emptyProfileIsDev && activeProfiles.isEmpty()) || activeProfiles.any { it in devProfiles }

        val violations = mutableListOf<String>()
        if (jwtSecret.contains(DEFAULT_JWT_SECRET_MARKER)) {
            violations += "JWT_SECRET is unset; the application.yaml default is in use."
        }
        if (jwtSecret.toByteArray().size < 32) {
            violations += "JWT_SECRET must be at least 32 bytes (256 bits) for HS256."
        }
        if (adminPassword == DEFAULT_ADMIN_PASSWORD) {
            violations += "SYNOPTIC_ADMIN_PASSWORD is the application.yaml default ('$DEFAULT_ADMIN_PASSWORD')."
        }

        if (violations.isEmpty()) return

        val joined = violations.joinToString("\n  - ", prefix = "  - ")
        if (isDevDeployment) {
            log.warn("Secrets-guard warnings (allowed on dev profiles $activeProfiles):\n$joined")
            return
        }
        throw IllegalStateException(
            "Refusing to start on profile $activeProfiles with insecure defaults:\n$joined\n" +
                "Set the corresponding env vars (JWT_SECRET, SYNOPTIC_ADMIN_PASSWORD) before booting.",
        )
    }

    private companion object {
        const val DEFAULT_JWT_SECRET_MARKER = "your-256-bit-secret-change-in-production"
        const val DEFAULT_ADMIN_PASSWORD = "Admin@123"
    }
}
