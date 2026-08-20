package com.pluxity.weekly.task.service

import com.pluxity.weekly.auth.authorization.AccessAction
import com.pluxity.weekly.auth.authorization.AccessPolicy
import com.pluxity.weekly.auth.authorization.CurrentUserProvider
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
import com.pluxity.weekly.task.event.TaskAssignedEvent
import com.pluxity.weekly.task.repository.TaskRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TaskService(
    private val taskRepository: TaskRepository,
    private val epicRepository: EpicRepository,
    private val userRepository: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val accessPolicy: AccessPolicy,
    private val assignmentService: EpicAssignmentService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun findAll(): List<TaskResponse> = search(TaskSearchFilter())

    fun search(filter: TaskSearchFilter): List<TaskResponse> {
        val user = currentUserProvider.get()
        val restrictedId = accessPolicy.restrictedAssigneeId(user)
        val scoped =
            filter.copy(
                epicIds = filter.epicIds ?: accessPolicy.visibleEpicIds(user),
                assigneeId = restrictedId ?: filter.assigneeId,
            )
        if (scoped.epicIds?.isEmpty() == true) return emptyList()
        return taskRepository.findByFilter(scoped).map { it.toResponse() }
    }

    fun findById(id: Long): TaskResponse {
        val user = currentUserProvider.get()
        val task = getTaskById(id)
        accessPolicy.require(user, task, AccessAction.VIEW)
        return task.toResponse()
    }

    @Transactional
    fun create(request: TaskRequest): Long {
        val user = currentUserProvider.get()
        val epic = validateAndLoadEpic(user, request)
        val newAssigneeId = resolveAssigneeId(user, request, epic)
        val savedTask =
            taskRepository.save(
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
            )
        if (newAssigneeId != null) {
            eventPublisher.publishEvent(
                TaskAssignedEvent(userId = newAssigneeId, taskId = savedTask.requiredId, taskName = savedTask.name),
            )
        }
        return savedTask.requiredId
    }

    @Transactional
    fun update(
        id: Long,
        request: TaskUpdateRequest,
    ) {
        val user = currentUserProvider.get()
        val task = getTaskById(id)
        accessPolicy.require(user, task, AccessAction.EDIT)
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
        val user = currentUserProvider.get()
        val task = getTaskById(id)
        accessPolicy.require(user, task, AccessAction.DELETE)
        taskRepository.delete(task)
    }

    @Transactional
    fun restore(id: Long): TaskResponse {
        val user = currentUserProvider.get()
        val task =
            taskRepository.findRawById(id)
                ?: throw CustomException(ErrorCode.NOT_FOUND_TASK, id)
        if (taskRepository.isParentProjectDeletedByTaskId(id)) {
            throw CustomException(ErrorCode.PARENT_PROJECT_DELETED)
        }
        if (taskRepository.isParentEpicDeletedByTaskId(id)) {
            throw CustomException(ErrorCode.PARENT_EPIC_DELETED)
        }
        accessPolicy.require(user, task, AccessAction.EDIT)

        taskRepository.restoreById(id)

        // 복구 직후라 권한은 위에서 이미 확인했다. findById 를 다시 타면 사용자 조회가 한 번 더 돈다
        return task.toResponse()
    }

    private fun validateAndLoadEpic(
        user: User,
        request: TaskRequest,
    ): Epic {
        val epic = getEpicById(request.epicId)
        accessPolicy.requireCreateTask(user, epic)
        if (request.status != TaskStatus.TODO) {
            throw CustomException(ErrorCode.INVALID_INITIAL_STATUS, request.status)
        }
        epic.ensureMutable("create task")
        ensureUniqueTaskName(request.epicId, request.name)
        return epic
    }

    private fun resolveAssigneeId(
        user: User,
        request: TaskRequest,
        epic: Epic,
    ): Long? {
        val newId = request.assigneeId?.takeIf { it != user.requiredId }
        assignmentService.ensureAssigned(user, newId, epic)
        return newId
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
