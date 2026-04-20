package com.pluxity.weekly.epic.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.dto.EpicAssignmentResponse
import com.pluxity.weekly.epic.dto.EpicRequest
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.dto.EpicUpdateRequest
import com.pluxity.weekly.epic.dto.toResponse
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.repository.TaskRepository
import com.pluxity.weekly.teams.event.TeamsNotificationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EpicService(
    private val epicRepository: EpicRepository,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val authorizationService: AuthorizationService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun findAll(): List<EpicResponse> = search(EpicSearchFilter())

    fun search(filter: EpicSearchFilter): List<EpicResponse> {
        val user = authorizationService.currentUser()
        val scoped = filter.copy(epicIds = filter.epicIds ?: authorizationService.visibleEpicIds(user))
        if (scoped.epicIds?.isEmpty() == true) return emptyList()
        return epicRepository.findByFilter(scoped).map { it.toResponse() }
    }

    fun findById(id: Long): EpicResponse = getEpicById(id).toResponse()

    @Transactional
    fun create(request: EpicRequest): Long {
        val user = authorizationService.currentUser()
        authorizationService.requireEpicManage(user, request.projectId)
        val project = getProjectById(request.projectId)
        if (project.status == ProjectStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, project.status, "create epic")
        }
        val epic =
            epicRepository.save(
                Epic(
                    project = project,
                    name = request.name,
                    description = request.description,
                    status = request.status,
                    startDate = request.startDate,
                    dueDate = request.dueDate,
                ),
            )
        request.userIds?.forEach { userId ->
            val assignee = getUserById(userId)
            epic.assign(assignee)
        }
        return epic.requiredId
    }

    @Transactional
    fun update(
        id: Long,
        request: EpicUpdateRequest,
    ) {
        val user = authorizationService.currentUser()
        val epic = getEpicById(id)
        authorizationService.requireEpicManage(user, epic.project.requiredId)

        request.status?.let { newStatus ->
            val allTasksDone =
                if (newStatus == EpicStatus.DONE) {
                    taskRepository.findByEpicId(id).let { tasks ->
                        tasks.isNotEmpty() && tasks.all { it.status == TaskStatus.DONE }
                    }
                } else {
                    false
                }
            epic.changeStatus(newStatus, allTasksDone)
        }

        epic.update(
            name = request.name,
            description = request.description,
            startDate = request.startDate,
            dueDate = request.dueDate,
        )
        request.userIds?.let { userIds ->
            val requestedUsers = userRepository.findAllById(userIds)

            epic.assignments
                .filter { it.user !in requestedUsers }
                .forEach { assignment ->
                    val removedUserId = assignment.user.requiredId
                    epic.unassign(assignment.user)
                    taskRepository.deleteByEpicIdAndAssigneeId(epic.requiredId, removedUserId)
                    eventPublisher.publishEvent(
                        TeamsNotificationEvent(removedUserId, "${epic.name} 에픽에서 해제되었습니다"),
                    )
                }

            requestedUsers
                .filter { user -> epic.assignments.none { it.user == user } }
                .forEach { newUser ->
                    epic.assign(newUser)
                    eventPublisher.publishEvent(
                        TeamsNotificationEvent(newUser.requiredId, "${epic.name} 에픽에 배정되었습니다"),
                    )
                }
        }
    }

    @Transactional
    fun delete(id: Long) {
        val user = authorizationService.currentUser()
        val epic = getEpicById(id)
        authorizationService.requireEpicManage(user, epic.project.requiredId)
        epicRepository.delete(epic)
    }

    // ── EpicAssignment ──

    fun findAssignments(epicId: Long): List<EpicAssignmentResponse> = getEpicById(epicId).assignments.map { it.toResponse() }

    @Transactional
    fun assign(
        epicId: Long,
        userId: Long,
    ) {
        val user = authorizationService.currentUser()
        authorizationService.requireEpicAssign(user, epicId)
        val epic = getEpicById(epicId)
        val assignee = getUserById(userId)
        if (epic.assignments.any { it.user == assignee }) {
            throw CustomException(ErrorCode.DUPLICATE_EPIC_ASSIGNMENT, userId, epicId)
        }
        epic.assign(assignee)
        eventPublisher.publishEvent(
            TeamsNotificationEvent(userId, "${epic.name} 에픽에 배정되었습니다"),
        )
    }

    @Transactional
    fun unassign(
        epicId: Long,
        userId: Long,
    ) {
        val user = authorizationService.currentUser()
        authorizationService.requireEpicAssign(user, epicId)
        val epic = getEpicById(epicId)
        val assignee = getUserById(userId)
        if (epic.assignments.none { it.user == assignee }) {
            throw CustomException(ErrorCode.NOT_FOUND_EPIC_ASSIGNMENT, epicId, userId)
        }
        epic.unassign(assignee)
        taskRepository.deleteByEpicIdAndAssigneeId(epicId, userId)
        eventPublisher.publishEvent(
            TeamsNotificationEvent(userId, "${epic.name} 에픽에서 해제되었습니다"),
        )
    }

    private fun getEpicById(id: Long): Epic =
        epicRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_EPIC, id)

    private fun getProjectById(id: Long): Project =
        projectRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_PROJECT, id)

    private fun getUserById(id: Long): User =
        userRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_USER, id)
}
