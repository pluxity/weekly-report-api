package com.pluxity.weekly.test

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class ContainerConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:16")

    @Bean
    fun redis(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT)

    @Bean
    fun redisProperties(redis: GenericContainer<*>): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar { registry ->
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }
        }

    companion object {
        private const val REDIS_PORT = 6379
    }
}
