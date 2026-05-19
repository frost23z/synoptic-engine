package com.synopticengine.api.shared.config

import com.synopticengine.api.shared.ActorContext
import com.synopticengine.api.shared.TenantContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.client.RestClient
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig {
    @Bean
    fun restClient(): RestClient = RestClient.create()

    /**
     * Replace Spring Boot's auto-configured `applicationTaskExecutor` with one
     * that propagates [TenantContext] across the `@Async` boundary. Listeners
     * like `WorkflowEngine` and `WebhookDispatcher` then see the same tenant
     * the event was published in.
     *
     * Marked `@Primary` so `@Async` resolves to this bean. Spring's
     * `AsyncConfigurer` interface is the more idiomatic hook, but configuring
     * it on `AsyncConfig` itself conflicts with the `@Configuration`
     * factory-method initialisation order in Spring Boot 4 — splitting into a
     * `@Primary` bean sidesteps that.
     */
    @Bean(name = ["applicationTaskExecutor", "taskExecutor"])
    @Primary
    fun tenantAwareApplicationTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 8
        executor.maxPoolSize = 32
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("tenant-async-")
        executor.setTaskDecorator(TenantPropagatingTaskDecorator())
        executor.initialize()
        return executor
    }
}

class TenantPropagatingTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val tenantId = TenantContext.get()
        val actorId = ActorContext.get()
        return Runnable {
            // Both contexts are propagated together so async listeners (workflow engine,
            // webhook dispatcher) and any cross-tenant write paths see the same actor
            // identity the publisher was running as.
            val wrapped: () -> Unit = {
                if (actorId != null) {
                    ActorContext.runAs(actorId) { runnable.run() }
                } else {
                    runnable.run()
                }
            }
            if (tenantId != null) {
                TenantContext.runAs(tenantId) { wrapped() }
            } else {
                wrapped()
            }
        }
    }
}
