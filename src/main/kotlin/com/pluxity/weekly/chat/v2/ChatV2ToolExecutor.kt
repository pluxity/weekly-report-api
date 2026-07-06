package com.pluxity.weekly.chat.v2

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.util.ChatScope
import com.pluxity.weekly.core.exception.CustomException
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
 * (search: AuthorizationService 조회 범위 / update: requireTaskOwner)
 *
 * 실행 실패는 예외로 터뜨리지 않고 {"error": "..."} 결과로 모델에게 되돌려,
 * 모델이 사용자에게 자연어로 안내하거나 다른 시도를 하게 한다 (agent 패턴).
 */
@Component
class ChatV2ToolExecutor(
    private val taskService: TaskService,
    private val objectMapper: ObjectMapper,
) {
    fun execute(
        toolName: String,
        argumentsJson: String,
        currentUserId: Long,
    ): String =
        try {
            when (toolName) {
                ChatV2Tools.SEARCH_TASKS -> searchTasks(argumentsJson, currentUserId)
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

    private fun searchTasks(
        argumentsJson: String,
        currentUserId: Long,
    ): String {
        val args = objectMapper.readValue(argumentsJson, SearchTasksArgs::class.java)
        val results =
            taskService.search(
                TaskSearchFilter(
                    name = args.name?.takeIf { it.isNotBlank() },
                    status = args.status?.let { runCatching { TaskStatus.valueOf(it) }.getOrNull() },
                    assigneeId = if (args.assigneeMe == true) currentUserId else null,
                    scopeStartDate = ChatScope.scopeStartDate(),
                ),
            )
        val compact =
            results.take(MAX_SEARCH_RESULTS).map {
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
        return objectMapper.writeValueAsString(
            mapOf("count" to results.size, "tasks" to compact),
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
        private const val MAX_SEARCH_RESULTS = 20
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchTasksArgs(
    val name: String? = null,
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
