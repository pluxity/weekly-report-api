package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.service.TaskService
import org.springframework.stereotype.Component

/**
 * 확정된 CUD 액션을 서버에서 직접 실행
 * beforeAction이 없을 때만 호출됨
 */
@Component
class ChatExecutor(
    private val projectService: ProjectService,
    private val epicService: EpicService,
    private val taskService: TaskService,
) {
    fun execute(action: LlmAction): Long? =
        when (ChatActionType.fromOrNull(action.action)) {
            ChatActionType.DELETE -> executeDelete(action)
            ChatActionType.REVIEW_REQUEST -> executeReviewRequest(action)
            ChatActionType.ASSIGN -> executeAssign(action)
            ChatActionType.UNASSIGN -> executeUnassign(action)
            else -> null
        }

    private fun executeReviewRequest(action: LlmAction): Long? {
        if (ChatTarget.fromOrNull(action.target) != ChatTarget.TASK) return null
        val id = action.id ?: return null
        taskService.requestReview(id)
        return id
    }

    private fun executeAssign(action: LlmAction): Long? {
        if (ChatTarget.fromOrNull(action.target) != ChatTarget.EPIC) return null
        val id = action.id ?: return null
        action.userIds?.forEach { userId ->
            epicService.assign(id, userId)
        }
        return id
    }

    private fun executeUnassign(action: LlmAction): Long? {
        if (ChatTarget.fromOrNull(action.target) != ChatTarget.EPIC) return null
        val id = action.id ?: return null
        action.removeUserIds?.forEach { userId ->
            epicService.unassign(id, userId)
        }
        return id
    }

    private fun executeDelete(action: LlmAction): Long? {
        val id = action.id ?: return null
        when (ChatTarget.fromOrNull(action.target)) {
            ChatTarget.PROJECT -> projectService.delete(id)
            ChatTarget.EPIC -> epicService.delete(id)
            ChatTarget.TASK -> taskService.delete(id)
            ChatTarget.TEAM, ChatTarget.REVIEW, null -> Unit
        }
        return id
    }
}
