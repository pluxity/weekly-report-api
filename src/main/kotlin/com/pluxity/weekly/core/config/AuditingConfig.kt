package com.pluxity.weekly.config

import com.pluxity.weekly.core.auditing.AuditorAwareImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class AuditingConfig {
    @Bean
    fun auditorAware(): AuditorAware<String> = AuditorAwareImpl()
}
