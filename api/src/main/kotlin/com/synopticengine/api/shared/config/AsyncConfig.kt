package com.synopticengine.api.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestClient

@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig {
    @Bean
    fun restClient(): RestClient = RestClient.create()
}
