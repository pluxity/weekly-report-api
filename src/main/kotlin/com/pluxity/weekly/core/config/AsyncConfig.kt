package com.pluxity.weekly.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor
import java.util.concurrent.Executor

/**
 * DelegatingSecurityContextAsyncTaskExecutor = 쓰레드 로컬에 securityContext 전파용
 */

@EnableAsync
@Configuration
class AsyncConfig {
    @Bean
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("async-notify-")
        executor.initialize()
        return DelegatingSecurityContextAsyncTaskExecutor(executor)
    }
}
