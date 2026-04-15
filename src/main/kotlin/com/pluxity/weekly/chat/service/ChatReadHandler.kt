package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatReadResponse
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.LlmAction
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
import java.time.LocalDate

@Component
class ChatReadHandler(
    private val taskService: TaskService,
    private val projectService: ProjectService,
    private val epicService: EpicService,
    private val teamService: TeamService,
) {
    fun handle(action: LlmAction): ChatReadResponse {
        val target = action.target ?: "task"
        val filters = action.filters ?: emptyMap()
        return when (target) {
            "task" ->
                ChatReadResponse(
                    tasks =
                        taskService
                            .search(buildTaskFilter(filters, action.id)),
                )
            "project" ->
                ChatReadResponse(
                    projects =
                        projectService
                            .search(buildProjectFilter(filters, action.id)),
                )
            "epic" ->
                ChatReadResponse(
                    epics =
                        epicService
                            .search(buildEpicFilter(filters, action.id)),
                )
            "team" ->
                ChatReadResponse(
                    teams = teamService.search(buildTeamFilter(filters)),
                )
            "review" ->
                ChatReadResponse(
                    pendingReviews = taskService.findPendingReviews(),
                )
            else ->
                ChatReadResponse(
                    tasks =
                        taskService
                            .search(buildTaskFilter(filters, action.id)),
                )
        }
    }

    private fun buildTaskFilter(
        filters: Map<String, Any?>,
        id: Long? = null,
    ): TaskSearchFilter =
        TaskSearchFilter(
            taskId = id,
            status = (filters["status"] as? String)?.let { TaskStatus.valueOf(it) },
            epicId = (filters["epic_id"] as? Number)?.toLong(),
            projectId = (filters["project_id"] as? Number)?.toLong(),
            assigneeId = (filters["assignee_id"] as? Number)?.toLong(),
            name = filters["name"] as? String,
            dueDateFrom = (filters["due_date_from"] as? String)?.let { LocalDate.parse(it) },
            dueDateTo = (filters["due_date_to"] as? String)?.let { LocalDate.parse(it) },
        )

    private fun buildProjectFilter(
        filters: Map<String, Any?>,
        id: Long? = null,
    ): ProjectSearchFilter =
        ProjectSearchFilter(
            projectIds = id?.let { listOf(it) },
            status = (filters["status"] as? String)?.let { ProjectStatus.valueOf(it) },
            name = filters["name"] as? String,
            pmId = (filters["pm_id"] as? Number)?.toLong(),
            dueDateFrom = (filters["due_date_from"] as? String)?.let { LocalDate.parse(it) },
            dueDateTo = (filters["due_date_to"] as? String)?.let { LocalDate.parse(it) },
        )

    private fun buildEpicFilter(
        filters: Map<String, Any?>,
        id: Long? = null,
    ): EpicSearchFilter =
        EpicSearchFilter(
            epicIds = id?.let { listOf(it) },
            status = (filters["status"] as? String)?.let { EpicStatus.valueOf(it) },
            name = filters["name"] as? String,
            projectId = (filters["project_id"] as? Number)?.toLong(),
            assigneeId = (filters["assignee_id"] as? Number)?.toLong(),
            dueDateFrom = (filters["due_date_from"] as? String)?.let { LocalDate.parse(it) },
            dueDateTo = (filters["due_date_to"] as? String)?.let { LocalDate.parse(it) },
        )

    private fun buildTeamFilter(filters: Map<String, Any?>): TeamSearchFilter =
        TeamSearchFilter(
            name = filters["name"] as? String,
        )
}
