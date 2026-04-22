package com.pluxity.weekly.chat.context

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.util.ChatScope
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.service.TeamService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * target별 CONTEXT 포함 데이터
 *
 * project → projects(simple) + users(PM)
 * epic    → projects > epics + users(전체)
 * task    → create: projects > epics / 그 외: projects > epics > tasks
 * team    → teams + users(전체)
 *
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
    fun build(
        target: ChatTarget,
        actions: List<ChatActionType>,
    ): String {
        val user = authorizationService.currentUser()

        authorizationService.checkChatPermission(user, target, actions)

        val today = LocalDate.now().toString()
        val todayDayOfWeek = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val userRef = UserRef(id = user.requiredId, name = user.name)

        val hasCreateOnly = ChatActionType.CREATE in actions && ChatActionType.UPDATE !in actions
        val excludeDone = ChatActionType.UPDATE in actions || ChatActionType.DELETE in actions

        val context: ChatContext =
            when (target) {
                ChatTarget.PROJECT -> buildProjectContext(today, todayDayOfWeek, userRef, excludeDone)
                ChatTarget.EPIC -> buildEpicContext(today, todayDayOfWeek, userRef, excludeDone)
                ChatTarget.TEAM -> buildTeamContext(today, todayDayOfWeek, userRef)
                ChatTarget.TASK, ChatTarget.REVIEW -> buildTaskContext(today, todayDayOfWeek, userRef, hasCreateOnly, excludeDone)
            }

        return objectMapper.writeValueAsString(context)
    }

    private fun buildProjectContext(
        today: String,
        todayDayOfWeek: String,
        user: UserRef,
        excludeDone: Boolean,
    ): ProjectContext {
        val projects =
            projectService
                .search(
                    ProjectSearchFilter(
                        excludeDone = excludeDone,
                        scopeStartDate = ChatScope.scopeStartDate(),
                    ),
                ).map { ProjectSimple(id = it.id, name = it.name, status = it.status.name) }
        return ProjectContext(
            today = today,
            todayDayOfWeek = todayDayOfWeek,
            user = user,
            projects = projects,
            users = findUsersByRole("PM", "PO"),
        )
    }

    private fun buildEpicContext(
        today: String,
        todayDayOfWeek: String,
        user: UserRef,
        excludeDone: Boolean,
    ): EpicContext {
        val projects = projectService.findAll()
        val epics =
            epicService.search(
                EpicSearchFilter(
                    excludeDone = excludeDone,
                    scopeStartDate = ChatScope.scopeStartDate(),
                ),
            )
        val epicsByProject = epics.groupBy { it.projectId }
        val projectList =
            projects.map { project ->
                ProjectWithEpics(
                    id = project.id,
                    name = project.name,
                    epics = (epicsByProject[project.id] ?: emptyList()).map { EpicRef(id = it.id, name = it.name) },
                )
            }
        return EpicContext(
            today = today,
            todayDayOfWeek = todayDayOfWeek,
            user = user,
            projects = projectList,
            users = findAllUsers(),
        )
    }

    private fun buildTaskContext(
        today: String,
        todayDayOfWeek: String,
        user: UserRef,
        createOnly: Boolean,
        excludeDone: Boolean,
    ): ChatContext =
        if (createOnly) {
            val activeEpics = epicService.findAll().filter { it.status != EpicStatus.DONE }
            TaskCreateContext(
                today = today,
                todayDayOfWeek = todayDayOfWeek,
                user = user,
                projects = groupByProject(activeEpics),
            )
        } else {
            val tasks =
                taskService.search(
                    TaskSearchFilter(
                        excludeDone = excludeDone,
                        scopeStartDate = ChatScope.scopeStartDate(),
                    ),
                )
            val tasksByEpicId = tasks.groupBy { it.epicId }
            val activeEpics = epicService.findAll()
            TaskContext(
                today = today,
                todayDayOfWeek = todayDayOfWeek,
                user = user,
                projects = groupByProjectFull(activeEpics, tasksByEpicId),
                users = findAllUsers(),
            )
        }

    private fun buildTeamContext(
        today: String,
        todayDayOfWeek: String,
        user: UserRef,
    ): TeamContext =
        TeamContext(
            today = today,
            todayDayOfWeek = todayDayOfWeek,
            user = user,
            teams = teamService.findAll().map { TeamRef(id = it.id, name = it.name) },
            users = findAllUsers(),
        )

    private fun groupByProject(epics: List<EpicResponse>): List<ProjectWithEpics> =
        epics
            .groupBy { it.projectId to it.projectName }
            .map { (key, epics) ->
                ProjectWithEpics(
                    id = key.first,
                    name = key.second,
                    epics = epics.map { EpicRef(id = it.id, name = it.name) },
                )
            }

    private fun groupByProjectFull(
        epics: List<EpicResponse>,
        tasksByEpicId: Map<Long, List<TaskResponse>>,
    ): List<ProjectWithEpicsAndTasks> =
        epics
            .groupBy { it.projectId to it.projectName }
            .map { (key, epics) ->
                ProjectWithEpicsAndTasks(
                    id = key.first,
                    name = key.second,
                    epics =
                        epics.map { epic ->
                            EpicWithTasks(
                                id = epic.id,
                                name = epic.name,
                                tasks =
                                    (tasksByEpicId[epic.id] ?: emptyList()).map { task ->
                                        TaskRef(
                                            id = task.id,
                                            name = task.name,
                                            status = task.status.name,
                                            progress = task.progress,
                                        )
                                    },
                            )
                        },
                )
            }

    private fun findUsersByRole(vararg roleNames: String): List<UserRef> =
        roleNames
            .flatMap { userRepository.findAllByRoleName(it) }
            .distinctBy { it.requiredId }
            .map { it.toRef() }

    private fun findAllUsers(): List<UserRef> =
        userRepository
            .findAllBy(Sort.by("name"))
            .map { it.toRef() }

    private fun User.toRef(): UserRef = UserRef(id = this.requiredId, name = this.name)
}
