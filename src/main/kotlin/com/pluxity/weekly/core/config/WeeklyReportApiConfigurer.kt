package com.pluxity.weekly.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WeeklyReportApiConfigurer {
    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Weekly Report API")
                    .description("Weekly Report Platform API Documentation")
                    .version("1.0.0")
                    .contact(Contact().name("Pluxity").email("support@pluxity.com"))
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("http://www.apache.org/licenses/LICENSE-2.0.html"),
                    ),
            )

    @Bean
    fun allApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("1. 전체")
            .pathsToMatch("/**")
            .build()

    @Bean
    fun authApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("2. 인증")
            .pathsToMatch("/auth/**")
            .pathsToExclude("/users/**", "/admin/**", "/other/**")
            .build()

    @Bean
    fun userApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("3. 사용자 API")
            .pathsToMatch("/users/**")
            .build()

    @Bean
    fun teamApi(): GroupedOpenApi = apiGroup("4. 팀 관리 API", "/teams/**")

    @Bean
    fun projectApi(): GroupedOpenApi = apiGroup("5. 프로젝트 관리 API", "/projects/**")

    @Bean
    fun epicApi(): GroupedOpenApi = apiGroup("6. 업무 그룹 관리 API", "/epics/**")

    @Bean
    fun taskApi(): GroupedOpenApi = apiGroup("7. 태스크 관리 API", "/tasks/**")

    @Bean
    fun chatApi(): GroupedOpenApi = apiGroup("8. 채팅 API", "/chat/**")

    @Bean
    fun dashboardApi(): GroupedOpenApi = apiGroup("9. 대시보드 API", "/dashboard/**")

    private fun apiGroup(
        group: String,
        vararg pathsToMatch: String,
    ): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group(group)
            .pathsToMatch(*pathsToMatch)
            .build()
}
