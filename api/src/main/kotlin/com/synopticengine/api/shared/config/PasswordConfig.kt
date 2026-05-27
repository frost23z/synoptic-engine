package com.synopticengine.api.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class PasswordConfig {
    /**
     * T6.8 — Strength 12 (~300 ms on modern hardware) provides meaningful
     * brute-force resistance without a noticeable UX penalty at low login
     * volumes. The default (10) was too cheap on fast CPUs.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)
}
