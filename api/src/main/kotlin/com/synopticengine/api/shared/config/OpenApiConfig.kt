package com.synopticengine.api.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * T6.5 — Expose Swagger UI with a pre-configured JWT bearer scheme so
 * frontend developers can test authenticated endpoints without manually
 * adding headers.
 *
 * UI:   /swagger-ui/index.html
 * Docs: /v3/api-docs
 *
 * Both paths are already permitted in [com.synopticengine.api.auth.config.SecurityConfig].
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Synoptic Engine API")
                    .version("1.0")
                    .description("CRM/ERP platform — Krayin parity + cross-company resource sharing"),
            ).addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME))
            .components(
                Components().addSecuritySchemes(
                    BEARER_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "Obtain a token via POST /auth/login and paste the accessToken here.",
                        ),
                ),
            )

    private companion object {
        const val BEARER_SCHEME = "bearerAuth"
    }
}
