package com.synopticengine.api.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
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

    /**
     * Give every endpoint a stable, human-readable `operationId` of the form
     * `controllerCamel + MethodPascal` (e.g. `GroupController.create` → `groupCreate`,
     * `ActivityController.attachTag` → `activityAttachTag`).
     *
     * By default springdoc derives operationIds from the bare method name and
     * disambiguates collisions across controllers with `_1`, `_2`, … suffixes —
     * which are meaningless and **shift whenever endpoints are added/removed**.
     * Stable ids are what a generated client/SDK (Hey API) turns into function
     * names, so they must not churn. Prefixing with the controller name removes
     * cross-controller collisions (e.g. the many `attachTag` methods become
     * `leadAttachTag`, `personAttachTag`, …) without per-endpoint annotations.
     */
    @Bean
    fun stableOperationIds(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            val controller =
                handlerMethod.beanType.simpleName
                    .removeSuffix("Controller")
                    .replaceFirstChar { it.lowercaseChar() }
            val method = handlerMethod.method.name.replaceFirstChar { it.uppercaseChar() }
            operation.operationId = "$controller$method"
            operation
        }

    private companion object {
        const val BEARER_SCHEME = "bearerAuth"
    }
}
