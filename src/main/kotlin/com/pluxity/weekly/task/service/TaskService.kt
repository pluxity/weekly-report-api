package com.pluxity.weekly.task.service

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.authorization.AuthorizationService
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.task.dto.ActionLink
import com.pluxity.weekly.task.dto.PendingReviewActions
import com.pluxity.weekly.task.dto.PendingReviewResponse
import com.pluxity.weekly.task.dto.TaskApprovalLogResponse
import com.pluxity.weekly.task.dto.TaskRequest
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.dto.TaskUpdateRequest
import com.pluxity.weekly.task.dto.toResponse
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskApprovalAction
import com.pluxity.weekly.task.entity.TaskApprovalLog
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.repository.TaskApprovalLogRepository
import com.pluxity.weekly.task.repository.TaskRepository
import com.pluxity.weekly.teams.event.TeamsNotificationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TaskService(
    private val taskRepository: TaskRepository,
    private val taskApprovalLogRepository: TaskApprovalLogRepository,
    private val epicRepository: EpicRepository,
    private val userRepository: UserRepository,
    private val authorizationService: AuthorizationService,
    private val eventPublisher: ApplicationEventPublisher,
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
        val epic = getEpicById(request.epicId)
        if (epic.status == EpicStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, epic.status, "create task")
        }
        if (taskRepository.existsByEpicIdAndName(request.epicId, request.name)) {
            throw CustomException(ErrorCode.DUPLICATE_TASK, request.epicId, request.name)
        }
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
                    assignee = request.assigneeId?.let { getUserById(it) } ?: user,
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
        if (task.status == TaskStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, task.status, "update")
        }
        request.status?.let { newStatus ->
            if (task.status == TaskStatus.IN_REVIEW || newStatus == TaskStatus.IN_REVIEW || newStatus == TaskStatus.DONE) {
                throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, task.status, newStatus)
            }
        }
        task.update(
            epic = request.epicId?.let { getEpicById(it) },
            name = request.name,
            description = request.description,
            status = request.status,
            progress = request.progress,
            startDate = request.startDate,
            dueDate = request.dueDate,
            assignee = request.assigneeId?.let { getUserById(it) },
        )
    }

    @Transactional
    fun requestReview(id: Long) {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireTaskOwner(user, task)
        if (task.status != TaskStatus.TODO && task.status != TaskStatus.IN_PROGRESS) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, task.status, TaskApprovalAction.REVIEW_REQUEST)
        }
        task.update(status = TaskStatus.IN_REVIEW)
        writeLog(task, user, TaskApprovalAction.REVIEW_REQUEST)
        task.epic.project.pmId?.let { pmId ->
            eventPublisher.publishEvent(
                TeamsNotificationEvent(
                    userId = pmId,
                    message = "[검수 요청] '${task.name}' 태스크가 검수 요청되었습니다. 검수자: ${user.name}",
                ),
            )
        }
    }

    @Transactional
    fun approve(id: Long) {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireTaskReviewer(user, task)
        if (task.status != TaskStatus.IN_REVIEW) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, task.status, TaskApprovalAction.APPROVE)
        }
        task.update(status = TaskStatus.DONE)
        writeLog(task, user, TaskApprovalAction.APPROVE)
        task.assignee?.requiredId?.let { assigneeId ->
            eventPublisher.publishEvent(
                TeamsNotificationEvent(
                    userId = assigneeId,
                    message = "[승인] '${task.name}' 태스크가 승인되었습니다.",
                ),
            )
        }
    }

    @Transactional
    fun reject(
        id: Long,
        reason: String?,
    ) {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireTaskReviewer(user, task)
        if (task.status != TaskStatus.IN_REVIEW) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, task.status, TaskApprovalAction.REJECT)
        }
        val normalizedReason = reason?.takeIf { it.isNotBlank() }
        task.update(status = TaskStatus.IN_PROGRESS)
        writeLog(task, user, TaskApprovalAction.REJECT, normalizedReason)
        task.assignee?.requiredId?.let { assigneeId ->
            val suffix = normalizedReason?.let { " 사유: $it" } ?: ""
            eventPublisher.publishEvent(
                TeamsNotificationEvent(
                    userId = assigneeId,
                    message = "[반려] '${task.name}' 태스크가 반려되었습니다.$suffix",
                ),
            )
        }
    }

    fun findPendingReviews(): List<PendingReviewResponse> {
        val user = authorizationService.currentUser()
        val scopedProjectIds = authorizationService.pmScopedProjectIds(user)
        val tasks =
            when {
                scopedProjectIds == null -> taskRepository.findByStatus(TaskStatus.IN_REVIEW)
                scopedProjectIds.isEmpty() -> emptyList()
                else -> taskRepository.findByStatusAndEpicProjectIdIn(TaskStatus.IN_REVIEW, scopedProjectIds)
            }
        if (tasks.isEmpty()) return emptyList()

        val taskIds = tasks.map { it.requiredId }
        val latestRequestedAt: Map<Long, java.time.LocalDateTime> =
            taskApprovalLogRepository
                .findLatestCreatedAtByTaskIdsAndAction(taskIds, TaskApprovalAction.REVIEW_REQUEST)
                .associate { row -> (row[0] as Number).toLong() to row[1] as java.time.LocalDateTime }

        return tasks
            .map { task ->
                val requestedAt = latestRequestedAt[task.requiredId] ?: task.updatedAt
                PendingReviewResponse(
                    taskId = task.requiredId,
                    taskName = task.name,
                    description = task.description,
                    projectId = task.epic.project.requiredId,
                    projectName = task.epic.project.name,
                    epicId = task.epic.requiredId,
                    epicName = task.epic.name,
                    assigneeId = task.assignee?.id,
                    assigneeName = task.assignee?.name,
                    dueDate = task.dueDate,
                    reviewRequestedAt = requestedAt,
                    actions =
                        PendingReviewActions(
                            approve = ActionLink(method = "POST", url = "/tasks/approve/${task.requiredId}"),
                            reject = ActionLink(method = "POST", url = "/tasks/reject/${task.requiredId}"),
                        ),
                )
            }.sortedBy { it.reviewRequestedAt }
    }

    fun findApprovalLogs(id: Long): List<TaskApprovalLogResponse> {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireEpicAccess(user, task.epic.requiredId)
        return taskApprovalLogRepository.findByTaskIdOrderByIdAsc(id).map { it.toResponse() }
    }

    private fun writeLog(
        task: Task,
        actor: User,
        action: TaskApprovalAction,
        reason: String? = null,
    ) {
        taskApprovalLogRepository.save(
            TaskApprovalLog(
                task = task,
                actor = actor,
                action = action,
                reason = reason,
            ),
        )
    }

    @Transactional
    fun delete(id: Long) {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireTaskOwner(user, task)
        taskRepository.delete(task)
    }

    private fun getTaskById(id: Long): Task =
        taskRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_TASK, id)

    private fun getEpicById(id: Long): Epic =
        epicRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_EPIC, id)

    private fun getUserById(id: Long): User =
        userRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_USER, id)
}
