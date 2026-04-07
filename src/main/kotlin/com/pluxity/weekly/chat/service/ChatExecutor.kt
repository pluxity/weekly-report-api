package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.epic.dto.EpicRequest
import com.pluxity.weekly.epic.dto.EpicUpdateRequest
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.dto.ProjectRequest
import com.pluxity.weekly.project.dto.ProjectUpdateRequest
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.dto.TaskRequest
import com.pluxity.weekly.task.dto.TaskUpdateRequest
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.dto.TeamRequest
import com.pluxity.weekly.team.dto.TeamUpdateRequest
import com.pluxity.weekly.team.service.TeamService
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 확정된 CUD 액션을 서버에서 직접 실행
 * beforeAction이 없을 때만 호출됨
 */
@Component
class ChatExecutor(
    private val projectService: ProjectService,
    private val epicService: EpicService,
    private val taskService: TaskService,
    private val teamService: TeamService,
) {
    fun execute(action: LlmAction): Long? =
        when (action.action) {
            "create" -> executeCreate(action)
            "update" -> executeUpdate(action)
            "delete" -> executeDelete(action)
            else -> null
        }

    private fun executeCreate(action: LlmAction): Long? {
        val name = action.name ?: return null
        return when (action.target) {
            "project" ->
                projectService.create(
                    ProjectRequest(
                        name = name,
                        description = action.description,
                        status = action.status?.let { ProjectStatus.valueOf(it) } ?: ProjectStatus.TODO,
                        startDate = action.startDate?.let { LocalDate.parse(it) },
                        dueDate = action.dueDate?.let { LocalDate.parse(it) },
                        pmId = action.pmId,
                    ),
                )
            "epic" -> {
                val projectId = action.projectId ?: return null
                epicService.create(
                    EpicRequest(
                        projectId = projectId,
                        name = name,
                        description = action.description,
                        status = action.status?.let { EpicStatus.valueOf(it) } ?: EpicStatus.TODO,
                        startDate = action.startDate?.let { LocalDate.parse(it) },
                        dueDate = action.dueDate?.let { LocalDate.parse(it) },
                        userIds = action.userIds,
                    ),
                )
            }
            "task" -> {
                val epicId = action.epicId ?: return null
                taskService.create(
                    TaskRequest(
                        epicId = epicId,
                        name = name,
                        description = action.description,
                        status = action.status?.let { TaskStatus.valueOf(it) } ?: TaskStatus.TODO,
                        progress = action.progress ?: 0,
                        startDate = action.startDate?.let { LocalDate.parse(it) },
                        dueDate = action.dueDate?.let { LocalDate.parse(it) },
                    ),
                )
            }
            "team" ->
                teamService.create(
                    TeamRequest(
                        name = name,
                        leaderId = action.leaderId,
                    ),
                )
            else -> null
        }
    }

    private fun executeUpdate(action: LlmAction): Long? {
        val id = action.id ?: return null
        when (action.target) {
            "project" ->
                projectService.update(
                    id,
                    ProjectUpdateRequest(
                        name = action.name,
                        description = action.description,
                        status = action.status?.let { ProjectStatus.valueOf(it) },
                        startDate = action.startDate?.let { LocalDate.parse(it) },
                        dueDate = action.dueDate?.let { LocalDate.parse(it) },
                        pmId = action.pmId,
                    ),
                )
            "epic" -> {
                epicService.update(
                    id,
                    EpicUpdateRequest(
                        projectId = action.projectId,
                        name = action.name,
                        description = action.description,
                        status = action.status?.let { EpicStatus.valueOf(it) },
                        startDate = action.startDate?.let { LocalDate.parse(it) },
                        dueDate = action.dueDate?.let { LocalDate.parse(it) },
                        userIds = action.userIds,
                    ),
                )
                action.removeUserIds?.forEach { userId ->
                    epicService.unassign(id, userId)
                }
            }
            "task" ->
                taskService.update(
                    id,
                    TaskUpdateRequest(
                        epicId = action.epicId,
                        name = action.name,
                        description = action.description,
                        status = action.status?.let { TaskStatus.valueOf(it) },
                        progress = action.progress,
                        startDate = action.startDate?.let { LocalDate.parse(it) },
                        dueDate = action.dueDate?.let { LocalDate.parse(it) },
                        assigneeId = action.assigneeId,
                    ),
                )
            "team" ->
                teamService.update(
                    id,
                    TeamUpdateRequest(
                        name = action.name,
                        leaderId = action.leaderId,
                    ),
                )
        }
        return id
    }

    private fun executeDelete(action: LlmAction): Long? {
        val id = action.id ?: return null
        when (action.target) {
            "project" -> projectService.delete(id)
            "epic" -> epicService.delete(id)
            "task" -> taskService.delete(id)
            "team" -> teamService.delete(id)
        }
        return id
    }
}
