package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatReadResponse
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.LlmActionFilters
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.dto.TeamSearchFilter
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.service.TeamService
import org.springframework.stereotype.Component

@Component
class ChatReadHandler(
    private val taskService: TaskService,
    private val projectService: ProjectService,
    private val epicService: EpicService,
    private val teamService: TeamService,
) {
    fun handle(action: LlmAction): ChatReadResponse {
        val target = ChatTarget.fromOrNull(action.target) ?: ChatTarget.TASK
        val filters = action.filters
        return when (target) {
            ChatTarget.TASK ->
                ChatReadResponse(
                    tasks = taskService.search(buildTaskFilter(filters, action.id)),
                )
            ChatTarget.PROJECT ->
                ChatReadResponse(
                    projects = projectService.search(buildProjectFilter(filters, action.id)),
                )
            ChatTarget.EPIC ->
                ChatReadResponse(
                    epics = epicService.search(buildEpicFilter(filters, action.id)),
                )
            ChatTarget.TEAM ->
                ChatReadResponse(
                    teams = teamService.search(buildTeamFilter(filters)),
                )
            ChatTarget.REVIEW ->
                ChatReadResponse(
                    pendingReviews = taskService.findPendingReviews(),
                )
        }
    }

    private fun buildTaskFilter(
        filters: LlmActionFilters?,
        id: Long? = null,
    ): TaskSearchFilter =
        TaskSearchFilter(
            taskId = id,
            status = filters?.status?.let { s -> TaskStatus.entries.find { it.name.equals(s, ignoreCase = true) } },
            epicId = filters?.epicId,
            projectId = filters?.projectId,
            assigneeId = filters?.assigneeId,
            name = filters?.name,
            dueDateFrom = filters?.dueDateFrom,
            dueDateTo = filters?.dueDateTo,
        )

    private fun buildProjectFilter(
        filters: LlmActionFilters?,
        id: Long? = null,
    ): ProjectSearchFilter =
        ProjectSearchFilter(
            projectIds = id?.let { listOf(it) },
            status = filters?.status?.let { s -> ProjectStatus.entries.find { it.name.equals(s, ignoreCase = true) } },
            name = filters?.name,
            pmId = filters?.pmId,
            dueDateFrom = filters?.dueDateFrom,
            dueDateTo = filters?.dueDateTo,
        )

    private fun buildEpicFilter(
        filters: LlmActionFilters?,
        id: Long? = null,
    ): EpicSearchFilter =
        EpicSearchFilter(
            epicIds = id?.let { listOf(it) },
            status = filters?.status?.let { s -> EpicStatus.entries.find { it.name.equals(s, ignoreCase = true) } },
            name = filters?.name,
            projectId = filters?.projectId,
            assigneeId = filters?.assigneeId,
            dueDateFrom = filters?.dueDateFrom,
            dueDateTo = filters?.dueDateTo,
        )

    private fun buildTeamFilter(filters: LlmActionFilters?): TeamSearchFilter =
        TeamSearchFilter(
            name = filters?.name,
        )
}
