package com.synopticengine.api.shared.config

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.shared.ActorContext
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
            // Primary: authenticated HTTP request thread (SecurityContextHolder).
            val principal =
                SecurityContextHolder
                    .getContext()
                    .authentication
                    ?.takeIf { it.isAuthenticated && it.principal != "anonymousUser" }
                    ?.principal

            val fromSecurity =
                when (principal) {
                    is UserPrincipal -> principal.id
                    is UUID -> principal
                    is String -> runCatching { UUID.fromString(principal) }.getOrNull()
                    else -> null
                }

            // Fallback: async threads (WorkflowEngine, DataImportService, scheduled workers)
            // propagate ActorContext via TenantPropagatingTaskDecorator instead of
            // SecurityContextHolder. This ensures createdBy/updatedBy are populated on
            // entities written outside of the HTTP request thread.
            Optional.ofNullable(fromSecurity ?: ActorContext.get())
        }
}
