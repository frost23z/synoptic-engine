package com.synopticengine.api.auth.config

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.auth.service.ApiKeyService
import com.synopticengine.api.shared.ActorContext
import com.synopticengine.api.shared.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * T6.8 — JWT parsed exactly once per request. All claim reads go through the
 * cached [io.jsonwebtoken.Claims] object returned by [JwtTokenProvider.parseClaimsOrNull],
 * eliminating the prior ~6 redundant parses per request.
 *
 * API keys (tokens starting with "sk_") are resolved via [ApiKeyService.authenticateByKey]
 * and follow the same principal/context setup path as JWTs.
 *
 * [SecurityContextHolder] is cleared in `finally` for virtual-thread safety:
 * virtual threads may be pooled and reused, so leaving a stale context would
 * bleed authentication across requests on the same carrier thread.
 */
@Component
class JwtAuthFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val apiKeyService: ApiKeyService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)

        if (token != null) {
            val principal =
                if (token.startsWith("sk_")) {
                    apiKeyService.authenticateByKey(token)
                } else {
                    resolveJwtPrincipal(token)
                }

            if (principal != null) {
                val authorities = principal.authorities.toList()
                val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)
                SecurityContextHolder.getContext().authentication = authentication
                TenantContext.set(principal.tenantId)
                ActorContext.set(principal.id)
            }
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
            TenantContext.clear()
            ActorContext.clear()
        }
    }

    private fun resolveJwtPrincipal(token: String): UserPrincipal? {
        val claims = jwtTokenProvider.parseClaimsOrNull(token) ?: return null
        if (!jwtTokenProvider.isAccessToken(claims)) return null
        val userId = jwtTokenProvider.getUserId(claims)
        val tenantId = jwtTokenProvider.getTenantId(claims)
        val email = jwtTokenProvider.getEmail(claims)
        val authorities = jwtTokenProvider.getAuthorities(claims).map { SimpleGrantedAuthority(it) }
        return UserPrincipal(id = userId, tenantId = tenantId, email = email, authorities = authorities)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substring(7).trim().takeIf { it.isNotEmpty() }
    }
}
