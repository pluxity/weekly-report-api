package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatDto
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.hasValueFor
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
        val type = ChatActionType.from(action.action)
        val target = ChatTarget.fromOrNull(action.target) ?: ChatTarget.TASK
        validate(action, type, target)

        return when (type) {
            ChatActionType.READ ->
                ChatActionResponse(
                    action = type.key,
                    target = target.key,
                    message = action.reply,
                    readResult = chatReadHandler.handle(action),
                )
            ChatActionType.CREATE, ChatActionType.UPDATE ->
                buildDtoResponse(action, type, target)
            ChatActionType.DELETE,
            ChatActionType.REVIEW_REQUEST,
            ChatActionType.ASSIGN,
            ChatActionType.UNASSIGN,
            ->
                ChatActionResponse(
                    action = type.key,
                    target = target.key,
                    message = action.reply,
                    id = chatExecutor.execute(action),
                )
            ChatActionType.CLARIFY -> error("CLARIFY는 validate에서 이미 처리됨")
            ChatActionType.ANSWER -> error("ANSWER는 ChatService에서 이미 처리됨")
        }
    }

    private fun validate(
        action: LlmAction,
        type: ChatActionType,
        target: ChatTarget,
    ) {
        // ANSWER는 원래 intent 단계에서 분기되지만, 2차 LLM이 출력하면 clarify로 강등해 500을 막는다
        if (type == ChatActionType.CLARIFY || type == ChatActionType.ANSWER) {
            throw ChatClarifyException(action.message ?: action.reply ?: "좀 더 구체적으로 말씀해주세요.")
        }
        if (target == ChatTarget.TEAM && type != ChatActionType.READ) {
            throw ChatClarifyException("팀 관리는 웹페이지에서 이용해주세요.")
        }
        if (type == ChatActionType.CREATE && target == ChatTarget.TASK) {
            val user = authorizationService.currentUser()
            val visibleEpics = authorizationService.visibleEpicIds(user)
            if (visibleEpics != null && visibleEpics.isEmpty()) {
                throw ChatClarifyException("태스크를 생성할 수 있는 업무 그룹이 없습니다. 먼저 업무 그룹에 참여해주세요.")
            }
            validateAssigneeAccess(action, user)
        }
        if (type.validatesMissingFields && !action.missingFields.isNullOrEmpty()) {
            if (action.missingFields.size > 1) {
                log.warn { "LLM이 복수 missingFields 반환: ${action.missingFields} — [0]만 사용" }
            }
            throwSelectOrClarify(action, action.missingFields.first())
        }
        type.requiredFields.firstOrNull { !action.hasValueFor(it) }?.let { missing ->
            throwSelectOrClarify(action, missing)
        }
    }

    private fun validateAssigneeAccess(
        action: LlmAction,
        user: User,
    ) {
        val assigneeId = action.assigneeId ?: return
        if (assigneeId == user.requiredId) return
        val accessibleUserIds =
            epicService
                .findAll()
                .flatMap { it.members.map { m -> m.userId } }
                .toSet()
        if (assigneeId !in accessibleUserIds) {
            throw ChatClarifyException("선택한 담당자는 접근 가능한 업무 그룹의 멤버가 아닙니다. 먼저 업무 그룹에 영입한 뒤 할당해주세요.")
        }
    }

    private fun throwSelectOrClarify(
        action: LlmAction,
        field: String,
    ): Nothing {
        val message = action.message ?: "대상을 특정할 수 없습니다."
        val resolved = selectFieldResolver.resolveCandidates(field, action)
        if (resolved.isEmpty()) throw ChatClarifyException(message)

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

    private fun buildDtoResponse(
        action: LlmAction,
        type: ChatActionType,
        target: ChatTarget,
    ): ChatActionResponse {
        val selectFields = selectFieldResolver.resolve(action)
        val changes = chatDtoMapper.toDto(action)
        val dto =
            if (type == ChatActionType.UPDATE && action.id != null) {
                val existing = loadExistingDto(target, action.id)
                if (existing != null && changes != null) chatDtoMapper.merge(existing, changes) else changes
            } else {
                changes
            }
        return ChatActionResponse(
            action = type.key,
            target = target.key,
            message = action.reply,
            id = if (type == ChatActionType.UPDATE) action.id else null,
            dto = dto,
            selectFields = selectFields.ifEmpty { null },
        )
    }

    private fun loadExistingDto(
        target: ChatTarget,
        id: Long,
    ): ChatDto? =
        when (target) {
            ChatTarget.TASK -> chatDtoMapper.fromTaskResponse(taskService.findById(id))
            ChatTarget.EPIC -> chatDtoMapper.fromEpicResponse(epicService.findById(id))
            ChatTarget.PROJECT -> chatDtoMapper.fromProjectResponse(projectService.findById(id))
            ChatTarget.TEAM, ChatTarget.REVIEW, ChatTarget.WEEKLY_REPORT -> null
        }
}
