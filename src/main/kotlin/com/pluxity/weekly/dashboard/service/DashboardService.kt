package com.pluxity.weekly.dashboard.service

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.dashboard.dto.AdminDashboardResponse
import com.pluxity.weekly.dashboard.dto.AdminProjectCard
import com.pluxity.weekly.dashboard.dto.EpicTaskGroup
import com.pluxity.weekly.dashboard.dto.EpicTaskRow
import com.pluxity.weekly.dashboard.dto.MemberTaskBar
import com.pluxity.weekly.dashboard.dto.MemberTaskSummary
import com.pluxity.weekly.dashboard.dto.PersonDetailResponse
import com.pluxity.weekly.dashboard.dto.PersonKpi
import com.pluxity.weekly.dashboard.dto.PmDashboardResponse
import com.pluxity.weekly.dashboard.dto.PmProjectSummary
import com.pluxity.weekly.dashboard.dto.ProjectParticipation
import com.pluxity.weekly.dashboard.dto.RecentTaskItem
import com.pluxity.weekly.dashboard.dto.RoadmapItem
import com.pluxity.weekly.dashboard.dto.RoadmapTaskBar
import com.pluxity.weekly.dashboard.dto.TeamSummaryItem
import com.pluxity.weekly.dashboard.dto.WorkerDashboardResponse
import com.pluxity.weekly.dashboard.dto.WorkerEpicItem
import com.pluxity.weekly.dashboard.dto.WorkerSummary
import com.pluxity.weekly.dashboard.dto.WorkerTaskItem
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.repository.TaskRepository
import com.pluxity.weekly.team.repository.TeamMemberRepository
import com.pluxity.weekly.team.repository.TeamRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class DashboardService(
    private val authorizationService: AuthorizationService,
    private val projectRepository: ProjectRepository,
    private val epicRepository: EpicRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
) {
    fun getWorkerDashboard(): WorkerDashboardResponse {
        val user = authorizationService.currentUser()
        val userId = user.requiredId

        val epics = epicRepository.findByAssignmentsUserIdWithProject(userId)
        val tasks =
            taskRepository.findByAssigneeId(userId)
        val tasksByEpicId = tasks.groupBy { it.epic.requiredId }
        val now = LocalDate.now()

        return WorkerDashboardResponse(
            summary = buildSummary(tasks, now),
            epics =
                epics.map { epic ->
                    val epicTasks = tasksByEpicId[epic.requiredId] ?: emptyList()
                    WorkerEpicItem(
                        epicId = epic.requiredId,
                        epicName = epic.name,
                        projectId = epic.project.requiredId,
                        projectName = epic.project.name,
                        status = epic.status,
                        progress = if (epicTasks.isEmpty()) 0 else epicTasks.map { it.progress }.average().toInt(),
                        startDate = epic.startDate,
                        dueDate = epic.dueDate,
                        tasks = epicTasks.map { it.toWorkerTaskItem(epic.dueDate) },
                    )
                },
        )
    }

    fun getPmDashboard(projectId: Long): PmDashboardResponse {
        val user = authorizationService.currentUser()
        authorizationService.requireProjectManager(user, projectId)

        val project =
            projectRepository.findByIdOrNull(projectId)
                ?: throw CustomException(ErrorCode.NOT_FOUND_PROJECT, projectId)

        val pmName =
            project.pmId?.let { userRepository.findByIdOrNull(it)?.name } ?: ""

        val epics = epicRepository.findByProjectId(projectId)
        val tasks = taskRepository.findByEpicIn(epics)
        val tasksByEpicId = tasks.groupBy { it.epic.requiredId }
        val now = LocalDate.now()

        val memberCount = tasks.mapNotNull { it.assignee?.requiredId }.distinct().size

        return PmDashboardResponse(
            project =
                PmProjectSummary(
                    projectId = project.requiredId,
                    projectName = project.name,
                    pmName = pmName,
                    status = project.status,
                    progress = if (tasks.isEmpty()) 0 else tasks.map { it.progress }.average().toInt(),
                    startDate = project.startDate,
                    dueDate = project.dueDate,
                    epicCount = epics.size,
                    taskCount = tasks.size,
                    memberCount = memberCount,
                ),
            roadmapItems =
                epics.map { epic ->
                    val epicTasks = tasksByEpicId[epic.requiredId] ?: emptyList()
                    RoadmapItem(
                        epicId = epic.requiredId,
                        epicName = epic.name,
                        startDate = epic.startDate,
                        dueDate = epic.dueDate,
                        status = epic.status,
                        progress = if (epicTasks.isEmpty()) 0 else epicTasks.map { it.progress }.average().toInt(),
                        tasks = epicTasks.map { it.toRoadmapTaskBar(now) },
                    )
                },
            epicTaskGroups =
                epics.map { epic ->
                    val epicTasks = tasksByEpicId[epic.requiredId] ?: emptyList()
                    EpicTaskGroup(
                        epicId = epic.requiredId,
                        epicName = epic.name,
                        status = epic.status,
                        tasks = epicTasks.map { it.toEpicTaskRow(now) },
                    )
                },
        )
    }

    fun getAdminDashboard(): AdminDashboardResponse {
        val user = authorizationService.currentUser()
        authorizationService.requireAdmin(user)

        val now = LocalDate.now()

        // 프로젝트 카드
        val projects = projectRepository.findAll()
        val allEpics = epicRepository.findByProjectIdIn(projects.map { it.requiredId })
        val allTasks = taskRepository.findByEpicIn(allEpics)
        val epicsByProjectId = allEpics.groupBy { it.project.requiredId }
        val tasksByEpicId = allTasks.groupBy { it.epic.requiredId }

        val pmIds = projects.mapNotNull { it.pmId }.distinct()
        val pmNameById =
            if (pmIds.isEmpty()) {
                emptyMap()
            } else {
                userRepository.findAllById(pmIds).associate { it.requiredId to it.name }
            }

        val projectCards =
            projects.map { project ->
                val projectEpics = epicsByProjectId[project.requiredId] ?: emptyList()
                val projectTasks = projectEpics.flatMap { tasksByEpicId[it.requiredId] ?: emptyList() }
                AdminProjectCard(
                    projectId = project.requiredId,
                    projectName = project.name,
                    pmName = project.pmId?.let { pmNameById[it] },
                    status = project.status,
                    progress = if (projectTasks.isEmpty()) 0 else projectTasks.map { it.progress }.average().toInt(),
                    epicCount = projectEpics.size,
                    memberCount = projectTasks.mapNotNull { it.assignee?.requiredId }.distinct().size,
                    delayedTaskCount =
                        projectTasks.count { task ->
                            task.dueDate != null &&
                                task.status != TaskStatus.DONE &&
                                now.isAfter(task.dueDate)
                        },
                    startDate = project.startDate,
                    dueDate = project.dueDate,
                )
            }

        // 팀 요약
        val teams = teamRepository.findAll()
        val tasksByAssigneeId = allTasks.groupBy { it.assignee?.requiredId }

        val teamSummaries =
            teams.map { team ->
                val members = teamMemberRepository.findByTeam(team)
                val memberUserIds = members.map { it.user.requiredId }.toSet()
                val memberTasks = memberUserIds.flatMap { tasksByAssigneeId[it] ?: emptyList() }
                val doneCount = memberTasks.count { it.status == TaskStatus.DONE }

                TeamSummaryItem(
                    teamId = team.requiredId,
                    teamName = team.name,
                    leaderName = team.leaderId?.let { pmNameById[it] ?: userRepository.findByIdOrNull(it)?.name },
                    memberCount = members.size,
                    activeTaskCount = memberTasks.count { it.status != TaskStatus.DONE },
                    completionRate = if (memberTasks.isEmpty()) 0 else (doneCount * 100 / memberTasks.size),
                )
            }

        return AdminDashboardResponse(
            projects = projectCards,
            teamSummaries = teamSummaries,
        )
    }

    fun getPersonDetail(userId: Long): PersonDetailResponse {
        val currentUser = authorizationService.currentUser()
        authorizationService.requireAdmin(currentUser)

        val targetUser =
            userRepository.findByIdOrNull(userId)
                ?: throw CustomException(ErrorCode.NOT_FOUND_USER, userId)

        val teamMemberships = teamMemberRepository.findByUserId(userId)
        val department = teamMemberships.firstOrNull()?.team?.name

        val tasks = taskRepository.findByAssigneeId(userId)
        val doneTasks = tasks.filter { it.status == TaskStatus.DONE }

        // KPI
        val completionRate = if (tasks.isEmpty()) 0 else (doneTasks.size * 100 / tasks.size)
        val onTimeTasks =
            doneTasks.filter { task ->
                task.dueDate != null && !task.updatedAt.toLocalDate().isAfter(task.dueDate)
            }
        val onTimeRate = if (doneTasks.isEmpty()) 0 else (onTimeTasks.size * 100 / doneTasks.size)
        val delayedDoneTasks =
            doneTasks.filter { task ->
                task.dueDate != null && task.updatedAt.toLocalDate().isAfter(task.dueDate)
            }
        val averageDelayDays =
            if (delayedDoneTasks.isEmpty()) {
                0.0
            } else {
                kotlin.math.round(
                    delayedDoneTasks
                        .map { ChronoUnit.DAYS.between(it.dueDate, it.updatedAt.toLocalDate()).toDouble() }
                        .average() * 100,
                ) / 100.0
            }

        // 최근 수정 태스크 10건
        val recentTasks =
            tasks
                .sortedByDescending { it.updatedAt }
                .take(RECENT_TASK_LIMIT)
                .map { task ->
                    RecentTaskItem(
                        taskId = task.requiredId,
                        taskName = task.name,
                        epicName = task.epic.name,
                        projectName = task.epic.project.name,
                        status = task.status,
                        progress = task.progress,
                        updatedAt = task.updatedAt,
                    )
                }

        // 프로젝트 참여 현황 (에픽 단위)
        val projectParticipations =
            tasks
                .groupBy { it.epic.requiredId }
                .map { (_, epicTasks) ->
                    val epic = epicTasks.first().epic
                    ProjectParticipation(
                        projectId = epic.project.requiredId,
                        projectName = epic.project.name,
                        epicName = epic.name,
                        taskCount = epicTasks.size,
                        completedCount = epicTasks.count { it.status == TaskStatus.DONE },
                    )
                }

        return PersonDetailResponse(
            userId = targetUser.requiredId,
            userName = targetUser.name,
            department = department,
            kpi =
                PersonKpi(
                    completionRate = completionRate,
                    onTimeRate = onTimeRate,
                    averageDelayDays = averageDelayDays,
                    activeTaskCount = tasks.count { it.status != TaskStatus.DONE },
                ),
            recentTasks = recentTasks,
            projectParticipations = projectParticipations,
        )
    }

    fun getTeamMemberTasks(teamId: Long): List<MemberTaskSummary> {
        val currentUser = authorizationService.currentUser()
        authorizationService.requireAdmin(currentUser)

        val team =
            teamRepository.findByIdOrNull(teamId)
                ?: throw CustomException(ErrorCode.NOT_FOUND_TEAM, teamId)
        val teamMembers = teamMemberRepository.findByTeam(team)
        val memberUserIds = teamMembers.map { it.user.requiredId }
        val tasksByUserId = taskRepository.findByAssigneeIdIn(memberUserIds).groupBy { it.assignee!!.requiredId }
        val now = LocalDate.now()

        return teamMembers.map { member ->
            val user = member.user
            val tasks = (tasksByUserId[user.requiredId] ?: emptyList())
            MemberTaskSummary(
                userId = user.requiredId,
                userName = user.name,
                departments = team.name,
                activeTasks =
                    tasks.map { task ->
                        MemberTaskBar(
                            taskId = task.requiredId,
                            taskName = task.name,
                            epicName = task.epic.name,
                            projectName = task.epic.project.name,
                            startDate = task.startDate,
                            dueDate = task.dueDate,
                            status = task.status,
                            progress = task.progress,
                            daysDelta = task.calculateDaysDelta(now),
                        )
                    },
            )
        }
    }

    private fun buildSummary(
        tasks: List<Task>,
        now: LocalDate,
    ): WorkerSummary =
        WorkerSummary(
            approachingDeadline =
                tasks.count { task ->
                    task.dueDate != null &&
                        task.status != TaskStatus.DONE &&
                        ChronoUnit.DAYS.between(now, task.dueDate) in 0..7
                },
            inProgress = tasks.count { it.status == TaskStatus.IN_PROGRESS },
            completed = tasks.count { it.status == TaskStatus.DONE },
            total = tasks.size,
        )

    private fun Task.toWorkerTaskItem(epicDueDate: LocalDate?): WorkerTaskItem =
        WorkerTaskItem(
            taskId = this.requiredId,
            taskName = this.name,
            status = this.status,
            progress = this.progress,
            dueDate = this.dueDate,
            daysUntilDue =
                if (epicDueDate != null && this.dueDate != null) {
                    ChronoUnit.DAYS.between(this.dueDate, epicDueDate).toInt()
                } else {
                    null
                },
        )

    private fun Task.toRoadmapTaskBar(now: LocalDate): RoadmapTaskBar =
        RoadmapTaskBar(
            taskId = this.requiredId,
            taskName = this.name,
            assigneeName = this.assignee?.name,
            startDate = this.startDate,
            dueDate = this.dueDate,
            status = this.status,
            progress = this.progress,
            daysDelta = calculateDaysDelta(now),
        )

    private fun Task.toEpicTaskRow(now: LocalDate): EpicTaskRow =
        EpicTaskRow(
            taskId = this.requiredId,
            taskName = this.name,
            status = this.status,
            progress = this.progress,
            assigneeName = this.assignee?.name,
            startDate = this.startDate,
            dueDate = this.dueDate,
            daysDelta = calculateDaysDelta(now),
        )

    companion object {
        private const val RECENT_TASK_LIMIT = 10
    }

    /**
     * daysDelta 계산:
     * - DONE: updatedAt(완료일) - dueDate (음수=조기완료, 양수=지연완료)
     * - 미완료 + 마감초과: now - dueDate (양수=지연중)
     * - 그 외: null
     */
    private fun Task.calculateDaysDelta(now: LocalDate): Int? {
        val due = this.dueDate ?: return null
        return when {
            this.status == TaskStatus.DONE ->
                ChronoUnit.DAYS.between(due, this.updatedAt.toLocalDate()).toInt()
            now.isAfter(due) ->
                ChronoUnit.DAYS.between(due, now).toInt()
            else -> null
        }
    }
}
