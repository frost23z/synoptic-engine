package com.synopticengine.api.auth.config

import com.synopticengine.api.shared.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

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

        if (token != null && jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
            val userId = jwtTokenProvider.getUserIdFromToken(token)
            val tenantId = jwtTokenProvider.getTenantIdFromToken(token)
            val email = jwtTokenProvider.getEmailFromToken(token)
            val authorities =
                jwtTokenProvider
                    .getAuthoritiesFromToken(token)
                    .map { SimpleGrantedAuthority(it) }

            val principal =
                UserPrincipal(
                    id = userId,
                    tenantId = tenantId,
                    email = email,
                    authorities = authorities,
                )

            val authentication =
                UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities,
                )

            SecurityContextHolder.getContext().authentication = authentication
            TenantContext.set(tenantId)
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substring(7).trim().takeIf { it.isNotEmpty() }
    }
}
