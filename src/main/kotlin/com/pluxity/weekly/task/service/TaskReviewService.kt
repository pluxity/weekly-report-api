package com.pluxity.weekly.task.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.task.dto.ActionLink
import com.pluxity.weekly.task.dto.PendingReviewActions
import com.pluxity.weekly.task.dto.PendingReviewResponse
import com.pluxity.weekly.task.dto.TaskApprovalLogResponse
import com.pluxity.weekly.task.dto.toResponse
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskApprovalAction
import com.pluxity.weekly.task.entity.TaskApprovalLog
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.event.TaskApprovedEvent
import com.pluxity.weekly.task.event.TaskRejectedEvent
import com.pluxity.weekly.task.event.TaskReviewRequestedEvent
import com.pluxity.weekly.task.repository.TaskApprovalLogRepository
import com.pluxity.weekly.task.repository.TaskRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class TaskReviewService(
    private val taskRepository: TaskRepository,
    private val taskApprovalLogRepository: TaskApprovalLogRepository,
    private val authorizationService: AuthorizationService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun requestReview(id: Long) {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireTaskOwner(user, task)
        task.requestReview()
        writeLog(task, user, TaskApprovalAction.REVIEW_REQUEST)
        task.epic.project.pmId?.let { pmId ->
            eventPublisher.publishEvent(
                TaskReviewRequestedEvent(
                    taskId = task.requiredId,
                    taskName = task.name,
                    projectName = task.epic.project.name,
                    epicName = task.epic.name,
                    requesterName = user.name,
                    pmId = pmId,
                ),
            )
        }
    }

    @Transactional
    fun approve(id: Long) {
        val user = authorizationService.currentUser()
        val task = getTaskById(id)
        authorizationService.requireTaskReviewer(user, task)
        task.approve()
        writeLog(task, user, TaskApprovalAction.APPROVE)
        task.assignee?.requiredId?.let { assigneeId ->
            eventPublisher.publishEvent(
                TaskApprovedEvent(
                    userId = assigneeId,
                    taskName = task.name,
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
        task.reject()
        val normalizedReason = reason?.takeIf { it.isNotBlank() }
        writeLog(task, user, TaskApprovalAction.REJECT, normalizedReason)
        task.assignee?.requiredId?.let { assigneeId ->
            eventPublisher.publishEvent(
                TaskRejectedEvent(
                    userId = assigneeId,
                    taskName = task.name,
                    reason = normalizedReason,
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
        val latestRequestedAt: Map<Long, LocalDateTime> =
            taskApprovalLogRepository
                .findLatestCreatedAtByTaskIdsAndAction(taskIds, TaskApprovalAction.REVIEW_REQUEST)
                .associate { it.taskId to it.requestedAt }
        val now = LocalDateTime.now()

        return tasks
            .map { task ->
                val requestedAt = latestRequestedAt[task.requiredId] ?: now
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
                            approve = ActionLink(method = "POST", url = "/tasks/${task.requiredId}/approve"),
                            reject = ActionLink(method = "POST", url = "/tasks/${task.requiredId}/reject"),
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

    private fun getTaskById(id: Long): Task =
        taskRepository.findWithEpicAndProjectById(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_TASK, id)
}
