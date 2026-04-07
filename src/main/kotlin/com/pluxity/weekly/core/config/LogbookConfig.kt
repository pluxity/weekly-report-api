package com.pluxity.weekly.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.zalando.logbook.HttpRequest
import org.zalando.logbook.Logbook
import org.zalando.logbook.Sink
import org.zalando.logbook.core.Conditions
import org.zalando.logbook.core.DefaultHttpLogWriter
import org.zalando.logbook.core.DefaultSink
import org.zalando.logbook.core.HeaderFilters
import org.zalando.logbook.json.JacksonJsonFieldBodyFilter
import org.zalando.logbook.json.JsonHttpLogFormatter
import java.util.function.Predicate

@Configuration
@EnableConfigurationProperties(LogbookProperties::class)
class LogbookConfig(
    private val properties: LogbookProperties,
) {
    companion object {
        private val DEFAULT_EXCLUDE_PATHS =
            listOf(
                "/actuator/",
                "/swagger-ui/",
                "/api-docs/",
                "/.well-known/",
                "/springwolf/",
            )
    }

    @Bean
    fun logbook(): Logbook {
        val allExcludePaths = DEFAULT_EXCLUDE_PATHS + properties.excludePaths

        val excludePredicate: Predicate<HttpRequest> =
            Predicate { req ->
                val path = req.path
                allExcludePaths.any { path.contains(it) }
            }
        val condition = Conditions.exclude(listOf(excludePredicate))

        val sink: Sink =
            DefaultSink(
                JsonHttpLogFormatter(),
                DefaultHttpLogWriter(),
            )

        return Logbook
            .builder()
            .condition(condition)
            .headerFilter(
                HeaderFilters.replaceHeaders(
                    { name, _ ->
                        name.equals("Cookie", ignoreCase = true) ||
                            name.equals("Set-Cookie", ignoreCase = true)
                    },
                    "<obfuscated>",
                ),
            ).bodyFilter(
                JacksonJsonFieldBodyFilter(
                    listOf("password"),
                    "<obfuscated>",
                ),
            ).sink(sink)
            .build()
    }
}
