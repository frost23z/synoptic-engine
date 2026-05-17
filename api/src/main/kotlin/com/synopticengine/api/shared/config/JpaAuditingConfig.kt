package com.synopticengine.api.shared.config

import com.synopticengine.api.auth.UserPrincipal
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.UUID

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
class JpaAuditingConfig {
    @Bean
    fun auditorProvider(): AuditorAware<UUID> =
        AuditorAware {
            val principal =
                SecurityContextHolder
                    .getContext()
                    .authentication
                    ?.takeIf { it.isAuthenticated && it.principal != "anonymousUser" }
                    ?.principal

            Optional.ofNullable(
                when (principal) {
                    is UserPrincipal -> principal.id
                    is UUID -> principal
                    is String -> runCatching { UUID.fromString(principal) }.getOrNull()
                    else -> null
                },
            )
        }
}
