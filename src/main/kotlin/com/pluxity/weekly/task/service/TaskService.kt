package com.pluxity.weekly.task.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.epic.service.EpicAssignmentService
import com.pluxity.weekly.task.dto.TaskRequest
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.dto.TaskUpdateRequest
import com.pluxity.weekly.task.dto.toResponse
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.repository.TaskRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TaskService(
    private val taskRepository: TaskRepository,
    private val epicRepository: EpicRepository,
    private val userRepository: UserRepository,
    private val authorizationService: AuthorizationService,
    private val assignmentService: EpicAssignmentService,
) {
    fun findAll(): List<TaskResponse> = search(TaskSearchFilter())

    fun search(filter: TaskSearchFilter): List<TaskResponse> {
        val user = authorizationService.currentUser()
        val restrictedId = authorizationService.restrictedAssigneeId(user)
        val scoped =
            filter.copy(
                epicIds = filter.epicIds ?: authorizationService.visibleEpicIds(user),
                assigneeId = restrictedId ?: filter.assigneeId,
            )
        if (scoped.epicIds?.isEmpty() == true) return emptyList()
        return taskRepository.findByFilter(scoped).map { it.toResponse() }
    }

    fun findById(id: Long): TaskResponse = getTaskById(id).toResponse()

    @Transactional
    fun create(request: TaskRequest): Long {
        val user = authorizationService.currentUser()
        authorizationService.requireEpicAccess(user, request.epicId)
        if (request.status != TaskStatus.TODO) {
            throw CustomException(ErrorCode.INVALID_INITIAL_STATUS, request.status)
        }
        val epic = getEpicById(request.epicId)
        epic.ensureMutable("create task")
        ensureUniqueTaskName(request.epicId, request.name)
        val newAssigneeId = request.assigneeId?.takeIf { it != user.requiredId }
        assignmentService.ensureAssigned(user, newAssigneeId, epic)
        return taskRepository
            .save(
                Task(
                    epic = epic,
                    name = request.name,
                    description = request.description,
                    status = request.status,
                    progress = request.progress,
                    startDate = request.startDate,
                    dueDate = request.dueDate,
                    assignee = newAssigneeId?.let { getUserById(it) } ?: user,
                ),
            ).requiredId
    }

    @Transactional
    fun update(
        id: Long,
        request: TaskUpdateRequest,
    ) {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireTaskOwner(user, task)
        task.ensureMutable()
        request.status?.let { task.changeStatus(it) }
        request.name?.takeIf { it != task.name }?.let { newName ->
            ensureUniqueTaskName(task.epic.requiredId, newName)
        }
        val newAssigneeId = request.assigneeId?.takeIf { it != task.assignee?.requiredId }
        assignmentService.ensureAssigned(user, newAssigneeId, task.epic)
        task.update(
            name = request.name,
            description = request.description,
            progress = request.progress,
            startDate = request.startDate,
            dueDate = request.dueDate,
            assignee = newAssigneeId?.let { getUserById(it) },
        )
    }

    @Transactional
    fun delete(id: Long) {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireTaskOwner(user, task)
        taskRepository.delete(task)
    }

    @Transactional
    fun restore(id: Long) {
        val user = authorizationService.currentUser()
        val task = taskRepository.findRawById(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_TASK, id)
        authorizationService.requireTaskOwner(user, task)

        taskRepository.restoreById(id)
    }

    private fun ensureUniqueTaskName(
        epicId: Long,
        name: String,
    ) {
        if (taskRepository.existsByEpicIdAndName(epicId, name)) {
            throw CustomException(ErrorCode.DUPLICATE_TASK, epicId, name)
        }
    }

    private fun getTaskById(id: Long): Task =
        taskRepository.findWithEpicAndProjectById(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_TASK, id)

    private fun getEpicById(id: Long): Epic =
        epicRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_EPIC, id)

    private fun getUserById(id: Long): User =
        userRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_USER, id)
}
