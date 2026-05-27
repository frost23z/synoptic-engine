package com.synopticengine.api.auth.config

import com.synopticengine.api.auth.UserPrincipal
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
 * [SecurityContextHolder] is cleared in `finally` for virtual-thread safety:
 * virtual threads may be pooled and reused, so leaving a stale context would
 * bleed authentication across requests on the same carrier thread.
 */
@Component
class JwtAuthFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)

        if (token != null) {
            val claims = jwtTokenProvider.parseClaimsOrNull(token)
            if (claims != null && jwtTokenProvider.isAccessToken(claims)) {
                val userId = jwtTokenProvider.getUserId(claims)
                val tenantId = jwtTokenProvider.getTenantId(claims)
                val email = jwtTokenProvider.getEmail(claims)
                val authorities =
                    jwtTokenProvider
                        .getAuthorities(claims)
                        .map { SimpleGrantedAuthority(it) }

                val principal = UserPrincipal(id = userId, tenantId = tenantId, email = email, authorities = authorities)
                val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)

                SecurityContextHolder.getContext().authentication = authentication
                TenantContext.set(tenantId)
                ActorContext.set(userId)
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

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substring(7).trim().takeIf { it.isNotEmpty() }
    }
}
