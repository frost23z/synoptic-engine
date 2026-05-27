package com.synopticengine.api.shared.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

/**
 * Validates that a tenant-supplied outbound URL is safe to POST to.
 *
 * Rules (applied at both save-time and send-time, defense-in-depth):
 *  1. Scheme must be `https` (or `http` only when the hostname matches an
 *     explicitly configured allow-list via `synoptic.outbound.allowed-http-hosts`).
 *  2. No user-info (credentials in the URL).
 *  3. Host resolves in DNS.
 *  4. No loopback addresses (127.0.0.0/8, ::1).
 *  5. No RFC-1918 private ranges: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16.
 *  6. No link-local (169.254.0.0/16, fe80::/10).
 *  7. No IPv6 ULA (fc00::/7 — first byte is 0xfc or 0xfd).
 *
 * // MULTI-NODE: this validator resolves DNS on the calling JVM; in a distributed
 * // deployment an attacker who controls DNS can use DNS rebinding to bypass the
 * // check between validation and the actual HTTP call. Mitigate at the infra layer
 * // by running outbound webhooks through a restricted egress proxy.
 */
@Component
class OutboundUrlValidator(
    @Value("\${synoptic.outbound.allowed-http-hosts:}") allowedHttpHostsCsv: String,
) {
    private val allowedHttpHosts: Set<String> =
        allowedHttpHostsCsv
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * Validates that [rawUrl] is safe to use as a webhook destination.
     * @throws IllegalArgumentException if the URL fails any safety check.
     */
    fun validate(rawUrl: String) {
        val uri =
            try {
                URI(rawUrl)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("Invalid webhook URL: ${e.message}")
            }

        val scheme =
            uri.scheme?.lowercase()
                ?: throw IllegalArgumentException("Webhook URL must have a scheme (https://…)")
        val host =
            uri.host?.lowercase()?.trimEnd('.')
                ?: throw IllegalArgumentException("Webhook URL must have a host")

        // Rule 1 — scheme
        when (scheme) {
            "https" -> {
                Unit
            }

            "http" -> {
                if (host !in allowedHttpHosts) {
                    throw IllegalArgumentException(
                        "Webhook URL must use https (http is only allowed for explicitly configured dev hosts)",
                    )
                }
            }

            else -> {
                throw IllegalArgumentException("Webhook URL scheme must be https, got: $scheme")
            }
        }

        // Rule 2 — no credentials
        if (!uri.userInfo.isNullOrBlank()) {
            throw IllegalArgumentException("Webhook URL must not contain credentials (user@…)")
        }

        // Rules 3–7 — resolve and inspect every address the host maps to
        val addresses =
            try {
                InetAddress.getAllByName(host).toList()
            } catch (e: Exception) {
                throw IllegalArgumentException("Webhook URL host '$host' could not be resolved: ${e.message}")
            }
        if (addresses.isEmpty()) {
            throw IllegalArgumentException("Webhook URL host '$host' resolved to no addresses")
        }
        addresses.forEach { addr -> checkAddress(addr, host) }
    }

    private fun checkAddress(
        addr: InetAddress,
        host: String,
    ) {
        val ip = addr.hostAddress
        if (addr.isLoopbackAddress) {
            throw IllegalArgumentException("Webhook URL resolves to a loopback address ($host → $ip)")
        }
        if (addr.isSiteLocalAddress) {
            throw IllegalArgumentException("Webhook URL resolves to a private/RFC-1918 address ($host → $ip)")
        }
        if (addr.isLinkLocalAddress) {
            throw IllegalArgumentException("Webhook URL resolves to a link-local address ($host → $ip)")
        }
        // Explicit metadata-endpoint check (169.254.169.254) — already covered by
        // link-local above, but stated explicitly for clarity.
        val raw = addr.address
        if (raw.size == 4 && raw[0] == 169.toByte() && raw[1] == 254.toByte()) {
            throw IllegalArgumentException("Webhook URL resolves to the cloud metadata endpoint ($host → $ip)")
        }
        // IPv6 ULA: fc00::/7 — first byte is 0xfc or 0xfd
        if (raw.size == 16 && (raw[0] == 0xfc.toByte() || raw[0] == 0xfd.toByte())) {
            throw IllegalArgumentException("Webhook URL resolves to an IPv6 ULA address ($host → $ip)")
        }
    }
}
