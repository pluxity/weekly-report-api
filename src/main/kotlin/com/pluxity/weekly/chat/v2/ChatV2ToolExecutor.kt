package com.pluxity.weekly.chat.v2

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.util.ChatScope
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.dto.TaskUpdateRequest
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.service.TaskService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

private val log = KotlinLogging.logger {}

/**
 * tool_calls 실행부. 모델은 실행을 "요청"만 하고, 실제 실행·권한은 기존 서비스가 담당한다.
 * (search: 각 Service.search의 AuthorizationService 조회 범위 / update: requireTaskOwner)
 *
 * 실행 실패는 예외로 터뜨리지 않고 {"error": "..."} 결과로 모델에게 되돌려,
 * 모델이 사용자에게 자연어로 안내하거나 다른 시도를 하게 한다 (agent 패턴).
 */
@Component
class ChatV2ToolExecutor(
    private val taskService: TaskService,
    private val epicService: EpicService,
    private val projectService: ProjectService,
    private val objectMapper: ObjectMapper,
) {
    fun execute(
        toolName: String,
        argumentsJson: String,
        currentUserId: Long,
    ): String =
        try {
            when (toolName) {
                ChatV2Tools.SEARCH_ITEMS -> searchItems(argumentsJson, currentUserId)
                ChatV2Tools.UPDATE_TASK -> updateTask(argumentsJson)
                else -> errorResult("알 수 없는 도구: $toolName")
            }
        } catch (e: CustomException) {
            log.info { "chat/v2 tool 실행 거부: $toolName — ${e.message}" }
            errorResult(e.message ?: "요청을 처리할 수 없습니다.")
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 tool 실행 실패: $toolName args=$argumentsJson" }
            errorResult("도구 실행 중 오류가 발생했습니다: ${e.message}")
        }

    /**
     * 태스크·업무 그룹·프로젝트 통합 검색.
     * 사용자가 타입을 안 붙이고 이름만 말하는 실사용 패턴에 맞춰 3계층을 한 번에 뒤진다.
     * 이름 매칭은 in-memory 토큰 매칭([ItemNameMatcher]) — "cctv API" ↔ "CCTV 목록 API".
     */
    private fun searchItems(
        argumentsJson: String,
        currentUserId: Long,
    ): String {
        val args = objectMapper.readValue(argumentsJson, SearchItemsArgs::class.java)
        val query = args.query?.trim().orEmpty()
        if (query.isBlank()) return errorResult("query가 비어 있습니다.")
        val type = args.type?.lowercase()
        val scopeStart = ChatScope.scopeStartDate()

        val tasks =
            if (type == null || type == "task") {
                taskService
                    .search(
                        TaskSearchFilter(
                            status = args.status?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() },
                            assigneeId = if (args.assigneeMe == true) currentUserId else null,
                            scopeStartDate = scopeStart,
                        ),
                    ).filter { ItemNameMatcher.matches(query, it.name) }
                    .take(MAX_RESULTS_PER_TYPE)
                    .map {
                        mapOf(
                            "id" to it.id,
                            "name" to it.name,
                            "project" to it.projectName,
                            "epic" to it.epicName,
                            "status" to it.status.name,
                            "progress" to it.progress,
                            "due_date" to it.dueDate?.toString(),
                            "assignee" to it.assigneeName,
                        )
                    }
            } else {
                emptyList()
            }

        val epics =
            if (type == null || type == "epic") {
                epicService
                    .search(EpicSearchFilter(scopeStartDate = scopeStart))
                    .filter { ItemNameMatcher.matches(query, it.name) }
                    .take(MAX_RESULTS_PER_TYPE)
                    .map {
                        mapOf(
                            "id" to it.id,
                            "name" to it.name,
                            "project" to it.projectName,
                            "status" to it.status.name,
                            "due_date" to it.dueDate?.toString(),
                        )
                    }
            } else {
                emptyList()
            }

        val projects =
            if (type == null || type == "project") {
                projectService
                    .search(ProjectSearchFilter(scopeStartDate = scopeStart))
                    .filter { ItemNameMatcher.matches(query, it.name) }
                    .take(MAX_RESULTS_PER_TYPE)
                    .map {
                        mapOf(
                            "id" to it.id,
                            "name" to it.name,
                            "status" to it.status.name,
                            "due_date" to it.dueDate?.toString(),
                            "pm" to it.pmName,
                        )
                    }
            } else {
                emptyList()
            }

        return objectMapper.writeValueAsString(
            mapOf(
                "tasks" to tasks,
                "epics" to epics,
                "projects" to projects,
            ),
        )
    }

    private fun updateTask(argumentsJson: String): String {
        val args = objectMapper.readValue(argumentsJson, UpdateTaskArgs::class.java)
        val status = args.status?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() }
        if (status == TaskStatus.IN_REVIEW || status == TaskStatus.DONE) {
            return errorResult("IN_REVIEW/DONE 상태는 이 도구로 지정할 수 없습니다. 리뷰 요청 흐름을 안내하세요.")
        }
        taskService.update(
            args.id,
            TaskUpdateRequest(
                name = args.name,
                description = args.description,
                status = status,
                progress = args.progress,
                startDate = args.startDate?.let { LocalDate.parse(it) },
                dueDate = args.dueDate?.let { LocalDate.parse(it) },
            ),
        )
        val updated = taskService.findById(args.id)
        return objectMapper.writeValueAsString(
            mapOf(
                "updated" to true,
                "task" to
                    mapOf(
                        "id" to updated.id,
                        "name" to updated.name,
                        "status" to updated.status.name,
                        "progress" to updated.progress,
                        "due_date" to updated.dueDate?.toString(),
                    ),
            ),
        )
    }

    private fun errorResult(message: String): String = objectMapper.writeValueAsString(mapOf("error" to message))

    companion object {
        private const val MAX_RESULTS_PER_TYPE = 10
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchItemsArgs(
    val query: String? = null,
    val type: String? = null,
    val status: String? = null,
    @param:JsonProperty("assignee_me")
    val assigneeMe: Boolean? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdateTaskArgs(
    val id: Long,
    val name: String? = null,
    val description: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    @param:JsonProperty("start_date")
    val startDate: String? = null,
    @param:JsonProperty("due_date")
    val dueDate: String? = null,
)
