package com.pluxity.weekly

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = ["com.pluxity"])
class WeeklyReportApplication

fun main(args: Array<String>) {
    runApplication<WeeklyReportApplication>(*args)
}
