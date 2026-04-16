package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatDto
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.exception.ChatClarifyException
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.service.TaskService
import org.springframework.stereotype.Component

@Component
class ChatActionRouter(
    private val chatReadHandler: ChatReadHandler,
    private val chatExecutor: ChatExecutor,
    private val chatDtoMapper: ChatDtoMapper,
    private val selectFieldResolver: SelectFieldResolver,
    private val taskService: TaskService,
    private val epicService: EpicService,
    private val projectService: ProjectService,
) {
    companion object {
        private val VALID_MISSING_FIELDS = setOf("id", "project_id", "epic_id")
    }

    fun route(action: LlmAction): ChatActionResponse {
        val target = action.target ?: "task"
        return when {
            action.action == "read" ->
                ChatActionResponse(
                    action = action.action,
                    target = target,
                    readResult = chatReadHandler.handle(action),
                )
            action.action == "clarify" -> throw ChatClarifyException(
                message = action.message ?: "좀 더 구체적으로 말씀해주세요.",
            )
            target == "team" && action.action != "read" -> throw ChatClarifyException(
                message = "팀 관리는 웹페이지에서 이용해주세요.",
            )
            else -> {
                val missingFields = action.missingFields?.filter { it in VALID_MISSING_FIELDS }
                val filteredAction = action.copy(missingFields = missingFields?.ifEmpty { null })
                when {
                    !missingFields.isNullOrEmpty() ||
                        (
                            filteredAction.action in listOf("update", "delete", "review_request", "assign", "unassign") &&
                                filteredAction.id == null
                        ) -> {
                        throw buildClarifyException(filteredAction)
                    }
                    filteredAction.action in listOf("create", "update") -> {
                        val selectFields = selectFieldResolver.resolve(filteredAction)
                        val dto =
                            if (filteredAction.action == "update" && filteredAction.id != null) {
                                val existing = loadExistingDto(target, filteredAction.id)
                                val changes = chatDtoMapper.toDto(filteredAction)
                                if (existing != null && changes != null) chatDtoMapper.merge(existing, changes) else changes
                            } else {
                                chatDtoMapper.toDto(filteredAction)
                            }
                        ChatActionResponse(
                            action = filteredAction.action,
                            target = target,
                            id = filteredAction.id,
                            dto = dto,
                            selectFields = selectFields.ifEmpty { null },
                        )
                    }
                    else -> {
                        val resultId = chatExecutor.execute(filteredAction)
                        ChatActionResponse(
                            action = filteredAction.action,
                            target = target,
                            id = resultId,
                        )
                    }
                }
            }
        }
    }

    private fun buildClarifyException(action: LlmAction): ChatClarifyException {
        val base = action.message ?: "대상을 특정할 수 없습니다."
        val candidates = action.candidates
        val missingFields = action.missingFields

        if (candidates.isNullOrEmpty() || missingFields.isNullOrEmpty()) {
            return ChatClarifyException(message = base)
        }

        val names =
            missingFields
                .firstNotNullOfOrNull { field ->
                    selectFieldResolver
                        .resolveCandidateNames(field, action.target, candidates)
                        .takeIf { it.isNotEmpty() }
                }.orEmpty()

        return ChatClarifyException(
            message = base,
            candidates = names.ifEmpty { null },
        )
    }

    private fun loadExistingDto(
        target: String,
        id: Long,
    ): ChatDto? =
        when (target) {
            "task" -> chatDtoMapper.fromTaskResponse(taskService.findById(id))
            "epic" -> chatDtoMapper.fromEpicResponse(epicService.findById(id))
            "project" -> chatDtoMapper.fromProjectResponse(projectService.findById(id))
            else -> null
        }
}
