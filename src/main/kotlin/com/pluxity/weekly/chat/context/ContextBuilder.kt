package com.pluxity.weekly.chat.context

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.authorization.AuthorizationService
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.service.TeamService
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.task.entity.TaskStatus
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

/**
 * target별 CONTEXT 포함 데이터
 *
 * project → projects(simple) + users(PM)
 * epic    → projects > epics + users(전체)
 * task    → create: projects > epics / 그 외: projects > epics > tasks
 * team    → teams + users(전체)
 *
 * users는 항상 포함 (read에서도 이름→ID 매칭 필요)
 * 조회 범위는 Service.search()에서 AuthorizationService 기반으로 제한
 */
@Component
@Transactional(readOnly = true)
class ContextBuilder(
    private val userRepository: UserRepository,
    private val projectService: ProjectService,
    private val epicService: EpicService,
    private val taskService: TaskService,
    private val teamService: TeamService,
    private val authorizationService: AuthorizationService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val SCOPE_WEEKS = 2L
    }

    private fun isWithinScope(startDate: LocalDate?): Boolean =
        startDate != null && startDate >= LocalDate.now().minusWeeks(SCOPE_WEEKS)

    fun build(
        target: String,
        actions: List<String>,
    ): String {
        val user = authorizationService.currentUser()

        authorizationService.checkChatPermission(user, target, actions)

        val context =
            mutableMapOf<String, Any?>(
                "today" to LocalDate.now().toString(),
                "today_day_of_week" to LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.KOREAN),
                "user" to mapOf("id" to user.requiredId, "name" to user.name),
            )

        val hasCreateOnly = "create" in actions && "update" !in actions
        val excludeDone = "update" in actions || "delete" in actions

        when (target) {
            "project" -> buildProjectContext(context, excludeDone)
            "epic" -> buildEpicContext(context, excludeDone)
            "team" -> buildTeamContext(context)
            else -> buildTaskContext(context, hasCreateOnly, excludeDone)
        }

        return objectMapper.writeValueAsString(context)
    }

    private fun buildProjectContext(context: MutableMap<String, Any?>, excludeDone: Boolean) {
        val projects = projectService.findAll()
            .filter { isWithinScope(it.startDate) || it.status != ProjectStatus.DONE }
            .filter { !excludeDone || it.status != ProjectStatus.DONE }
        context["projects"] =
            projects.map {
                mapOf("id" to it.id, "name" to it.name, "status" to it.status.name)
            }
        context["users"] = findUsersByRole("PM")
    }

    private fun buildEpicContext(context: MutableMap<String, Any?>, excludeDone: Boolean) {
        val projects = projectService.findAll()
        val epics = epicService.findAll()
            .filter { isWithinScope(it.startDate) || it.status != EpicStatus.DONE }
            .filter { !excludeDone || it.status != EpicStatus.DONE }
        val epicsByProject = epics.groupBy { it.projectId }
        context["projects"] =
            projects
                .filter { epicsByProject.containsKey(it.id) }
                .map { project ->
                    mapOf(
                        "id" to project.id,
                        "name" to project.name,
                        "epics" to
                            (epicsByProject[project.id] ?: emptyList()).map {
                                mapOf("id" to it.id, "name" to it.name)
                            },
                    )
                }
        context["users"] = findAllUsers()
    }

    private fun buildTaskContext(
        context: MutableMap<String, Any?>,
        createOnly: Boolean,
        excludeDone: Boolean,
    ) {
        val epics = epicService.findAll()

        if (createOnly) {
            val activeEpics = epics.filter { it.status != EpicStatus.DONE }
            context["projects"] = groupByProject(activeEpics)
        } else {
            val tasks = taskService.findAll()
                .filter { isWithinScope(it.startDate) || it.status != TaskStatus.DONE }
                .filter { !excludeDone || it.status != TaskStatus.DONE }
            val tasksByEpicId = tasks.groupBy { it.epicId }
            context["projects"] = groupByProjectFull(epics, tasksByEpicId)
            context["users"] = findAllUsers()
        }
    }

    private fun buildTeamContext(context: MutableMap<String, Any?>) {
        context["teams"] = teamService.findAll().map { mapOf("id" to it.id, "name" to it.name) }
        context["users"] = findAllUsers()
    }

    private fun groupByProject(epics: List<EpicResponse>): List<Map<String, Any?>> =
        epics
            .groupBy { it.projectId to it.projectName }
            .map { (key, epics) ->
                mapOf(
                    "id" to key.first,
                    "name" to key.second,
                    "epics" to epics.map { mapOf("id" to it.id, "name" to it.name) },
                )
            }

    private fun groupByProjectFull(
        epics: List<EpicResponse>,
        tasksByEpicId: Map<Long, List<TaskResponse>>,
    ): List<Map<String, Any?>> =
        epics
            .groupBy { it.projectId to it.projectName }
            .map { (key, epics) ->
                mapOf(
                    "id" to key.first,
                    "name" to key.second,
                    "epics" to
                        epics.map { epic ->
                            mapOf(
                                "id" to epic.id,
                                "name" to epic.name,
                                "tasks" to
                                    (tasksByEpicId[epic.id] ?: emptyList()).map { task ->
                                        mapOf(
                                            "id" to task.id,
                                            "name" to task.name,
                                            "status" to task.status,
                                            "progress" to task.progress,
                                        )
                                    },
                            )
                        },
                )
            }

    private fun findUsersByRole(roleName: String): List<Map<String, Any?>> =
        userRepository
            .findAllBy(Sort.by("name"))
            .filter { user -> user.userRoles.any { it.role.name.uppercase() == roleName } }
            .map { mapOf("id" to it.requiredId, "name" to it.name) }

    private fun findAllUsers(): List<Map<String, Any?>> =
        userRepository
            .findAllBy(Sort.by("name"))
            .map { mapOf("id" to it.requiredId, "name" to it.name) }
}
