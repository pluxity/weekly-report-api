package com.pluxity.weekly.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EntityScan(basePackages = ["com.pluxity"])
@EnableJpaRepositories(basePackages = ["com.pluxity"])
class JpaConfig
