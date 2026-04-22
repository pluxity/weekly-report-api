package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatDto
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.exception.ChatClarifyException
import com.pluxity.weekly.chat.exception.ChatSelectRequiredException
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.service.TaskService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class ChatActionRouter(
    private val chatReadHandler: ChatReadHandler,
    private val chatExecutor: ChatExecutor,
    private val chatDtoMapper: ChatDtoMapper,
    private val selectFieldResolver: SelectFieldResolver,
    private val clarifyStore: ClarifyStore,
    private val authorizationService: AuthorizationService,
    private val taskService: TaskService,
    private val epicService: EpicService,
    private val projectService: ProjectService,
) {
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
            action.action == "create" -> {
                val selectFields = selectFieldResolver.resolve(action)
                val dto = chatDtoMapper.toDto(action)
                ChatActionResponse(
                    action = action.action,
                    target = target,
                    dto = dto,
                    selectFields = selectFields.ifEmpty { null },
                )
            }
            action.id == null ||
                (
                    action.action in listOf("delete", "review_request", "assign", "unassign") &&
                        !action.missingFields.isNullOrEmpty()
                ) ||
                (action.action == "assign" && action.userIds.isNullOrEmpty()) ||
                (action.action == "unassign" && action.removeUserIds.isNullOrEmpty()) ->
                throwSelectOrClarify(action)
            action.action == "update" -> {
                val selectFields = selectFieldResolver.resolve(action)
                val existing = loadExistingDto(target, action.id)
                val changes = chatDtoMapper.toDto(action)
                val dto = if (existing != null && changes != null) chatDtoMapper.merge(existing, changes) else changes
                ChatActionResponse(
                    action = action.action,
                    target = target,
                    id = action.id,
                    dto = dto,
                    selectFields = selectFields.ifEmpty { null },
                )
            }
            else -> {
                val resultId = chatExecutor.execute(action)
                ChatActionResponse(
                    action = action.action,
                    target = target,
                    id = resultId,
                )
            }
        }
    }

    private fun throwSelectOrClarify(action: LlmAction): Nothing {
        val message = action.message ?: "대상을 특정할 수 없습니다."
        val missingFields = action.missingFields
        if ((missingFields?.size ?: 0) > 1) {
            log.warn { "LLM이 복수 missingFields 반환: $missingFields — [0]만 사용" }
        }

        val field = missingFields?.firstOrNull() ?: nextMissingField(action)
        if (field != null) {
            val resolved = selectFieldResolver.resolveCandidates(field, action)
            if (resolved.isNotEmpty()) {
                val userId = authorizationService.currentUser().requiredId
                val normalized =
                    action.copy(
                        missingFields = listOf(field),
                        candidates = resolved.mapNotNull { it.id.toLongOrNull() },
                    )
                val clarifyId = clarifyStore.save(userId, normalized)
                throw ChatSelectRequiredException(
                    message = message,
                    clarifyId = clarifyId,
                    field = field,
                    candidates = resolved,
                )
            }
        }
        throw ChatClarifyException(message)
    }

    private fun nextMissingField(action: LlmAction): String? =
        when (action.action) {
            "assign" if action.userIds.isNullOrEmpty() -> "user_ids"
            "unassign" if action.removeUserIds.isNullOrEmpty() -> "remove_user_ids"
            else -> null
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
