package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatDto
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.dto.EpicChatDto
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.ProjectChatDto
import com.pluxity.weekly.chat.dto.TaskChatDto
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.task.dto.TaskResponse
import org.springframework.stereotype.Component

@Component
class ChatDtoMapper {
    fun toDto(action: LlmAction): ChatDto? {
        val type = ChatActionType.fromOrNull(action.action)
        if (type == null || type == ChatActionType.CLARIFY || type == ChatActionType.READ) return null

        return when (ChatTarget.fromOrNull(action.target)) {
            ChatTarget.PROJECT -> toProjectDto(action)
            ChatTarget.EPIC -> toEpicDto(action)
            ChatTarget.TASK -> toTaskDto(action)
            ChatTarget.TEAM, ChatTarget.REVIEW, null -> null
        }
    }

    private fun toProjectDto(action: LlmAction) =
        ProjectChatDto(
            name = action.name,
            description = action.description,
            status = action.status,
            startDate = action.startDate,
            dueDate = action.dueDate,
            pmId = action.pmId,
        )

    private fun toEpicDto(action: LlmAction) =
        EpicChatDto(
            name = action.name,
            projectId = action.projectId,
            description = action.description,
            status = action.status,
            startDate = action.startDate,
            dueDate = action.dueDate,
            userIds = action.userIds,
        )

    private fun toTaskDto(action: LlmAction) =
        TaskChatDto(
            name = action.name,
            epicId = action.epicId,
            description = action.description,
            status = action.status,
            progress = action.progress,
            startDate = action.startDate,
            dueDate = action.dueDate,
            assigneeId = action.assigneeId,
        )

    // ── Response → ChatDto 변환 ──

    fun fromTaskResponse(response: TaskResponse) =
        TaskChatDto(
            name = response.name,
            epicId = response.epicId,
            description = response.description,
            status = response.status.name,
            progress = response.progress,
            startDate = response.startDate?.toString(),
            dueDate = response.dueDate?.toString(),
            assigneeId = response.assigneeId,
        )

    fun fromEpicResponse(response: EpicResponse) =
        EpicChatDto(
            name = response.name,
            projectId = response.projectId,
            description = response.description,
            status = response.status.name,
            startDate = response.startDate?.toString(),
            dueDate = response.dueDate?.toString(),
            userIds = response.members.map { it.userId }.ifEmpty { null },
        )

    fun fromProjectResponse(response: ProjectResponse) =
        ProjectChatDto(
            name = response.name,
            description = response.description,
            status = response.status.name,
            startDate = response.startDate?.toString(),
            dueDate = response.dueDate?.toString(),
            pmId = response.pmId,
        )

    // ── merge: 기존값 + LLM 변경값 (non-null만 덮어씌움) ──

    fun merge(
        existing: ChatDto,
        changes: ChatDto,
    ): ChatDto =
        when {
            existing is TaskChatDto && changes is TaskChatDto ->
                TaskChatDto(
                    name = changes.name ?: existing.name,
                    epicId = changes.epicId ?: existing.epicId,
                    description = changes.description ?: existing.description,
                    status = changes.status ?: existing.status,
                    progress = changes.progress ?: existing.progress,
                    startDate = changes.startDate ?: existing.startDate,
                    dueDate = changes.dueDate ?: existing.dueDate,
                    assigneeId = changes.assigneeId ?: existing.assigneeId,
                )
            existing is EpicChatDto && changes is EpicChatDto ->
                EpicChatDto(
                    name = changes.name ?: existing.name,
                    projectId = changes.projectId ?: existing.projectId,
                    description = changes.description ?: existing.description,
                    status = changes.status ?: existing.status,
                    startDate = changes.startDate ?: existing.startDate,
                    dueDate = changes.dueDate ?: existing.dueDate,
                    userIds = changes.userIds ?: existing.userIds,
                )
            existing is ProjectChatDto && changes is ProjectChatDto ->
                ProjectChatDto(
                    name = changes.name ?: existing.name,
                    description = changes.description ?: existing.description,
                    status = changes.status ?: existing.status,
                    startDate = changes.startDate ?: existing.startDate,
                    dueDate = changes.dueDate ?: existing.dueDate,
                    pmId = changes.pmId ?: existing.pmId,
                )
            else -> changes
        }
}
