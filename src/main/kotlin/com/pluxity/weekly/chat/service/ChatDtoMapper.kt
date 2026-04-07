package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatDto
import com.pluxity.weekly.chat.dto.EpicChatDto
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.ProjectChatDto
import com.pluxity.weekly.chat.dto.TaskChatDto
import com.pluxity.weekly.chat.dto.TeamChatDto
import org.springframework.stereotype.Component

@Component
class ChatDtoMapper {
    fun toDto(action: LlmAction): ChatDto? {
        if (action.action == "clarify" || action.action == "read") return null

        return when (action.target) {
            "project" -> toProjectDto(action)
            "epic" -> toEpicDto(action)
            "task" -> toTaskDto(action)
            "team" -> toTeamDto(action)
            else -> null
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

    private fun toTeamDto(action: LlmAction) =
        TeamChatDto(
            name = action.name,
            leaderId = action.leaderId,
        )
}
