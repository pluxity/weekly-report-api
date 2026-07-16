package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.v2.dto.SearchUsersArgs
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * search_users 실행부 — 사용자 검색(id·역할 확인). 이름은 [ItemNameMatcher] 토큰 매칭, role은 대소문자 무시.
 */
@Component
class SearchUsersHandler(
    private val userRepository: UserRepository,
    private val support: ChatV2ToolSupport,
    private val objectMapper: ObjectMapper,
) {
    fun handle(
        argumentsJson: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = support.readArgs<SearchUsersArgs>(argumentsJson)
        val query = args.query?.trim()?.takeIf { it.isNotBlank() }
        val role = args.role?.trim()?.takeIf { it.isNotBlank() }
        val users =
            userRepository
                .findAllBy(Sort.by("name"))
                .filter { query == null || ItemNameMatcher.matches(query, it.name) }
                .filter { user -> role == null || user.userRoles.any { it.role.name.equals(role, ignoreCase = true) } }
                .distinctBy { it.requiredId }
                .take(MAX_RESULTS)
                .onEach { idRegistry.register(ChatV2EntityType.USER, it.requiredId) }
                .map {
                    mapOf(
                        "id" to it.requiredId,
                        "name" to it.name,
                        "roles" to it.getRoles().map { r -> r.name },
                    )
                }
        return objectMapper.writeValueAsString(mapOf("users" to users))
    }

    companion object {
        private const val MAX_RESULTS = 20
    }
}
