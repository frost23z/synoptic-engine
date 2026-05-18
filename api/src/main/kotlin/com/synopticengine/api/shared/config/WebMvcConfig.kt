package com.synopticengine.api.shared.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val tenantFilterInterceptor: TenantFilterInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        // Order matters: OpenEntityManagerInViewInterceptor (Spring Boot's auto-configured
        // OSIV interceptor) must run first so it binds an EntityManager to the thread before
        // we try to unwrap one and enable the Hibernate tenant filter on its session.
        registry.addInterceptor(tenantFilterInterceptor).order(Ordered.LOWEST_PRECEDENCE)
    }
}
