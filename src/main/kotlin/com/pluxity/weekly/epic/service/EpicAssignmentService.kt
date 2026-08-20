package com.pluxity.weekly.epic.service

import com.pluxity.weekly.auth.authorization.AccessAction
import com.pluxity.weekly.auth.authorization.AccessPolicy
import com.pluxity.weekly.auth.authorization.CurrentUserProvider
import com.pluxity.weekly.auth.authorization.UserType
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.dto.EpicAssignmentResponse
import com.pluxity.weekly.epic.dto.toResponse
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.event.EpicAssignedEvent
import com.pluxity.weekly.epic.event.EpicUnassignedEvent
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.task.repository.TaskRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EpicAssignmentService(
    private val epicRepository: EpicRepository,
    private val userRepository: UserRepository,
    private val taskRepository: TaskRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val accessPolicy: AccessPolicy,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun findByEpic(epicId: Long): List<EpicAssignmentResponse> = getEpicById(epicId).assignments.map { it.toResponse() }

    @Transactional
    fun assign(
        epicId: Long,
        userId: Long,
    ) {
        val user = currentUserProvider.get()
        val epic = getEpicById(epicId)
        accessPolicy.require(user, epic, AccessAction.ASSIGN)
        epic.ensureMutable("assign")
        val assignee = getUserById(userId)
        if (epic.assignments.any { it.user == assignee }) {
            throw CustomException(ErrorCode.DUPLICATE_EPIC_ASSIGNMENT, userId, epicId)
        }
        assignAndNotify(epic, assignee)
    }

    @Transactional
    fun unassign(
        epicId: Long,
        userId: Long,
    ) {
        val user = currentUserProvider.get()
        val epic = getEpicById(epicId)
        accessPolicy.require(user, epic, AccessAction.ASSIGN)
        epic.ensureMutable("unassign")
        val assignee = getUserById(userId)
        if (epic.assignments.none { it.user == assignee }) {
            throw CustomException(ErrorCode.NOT_FOUND_EPIC_ASSIGNMENT, epicId, userId)
        }
        unassignAndNotify(epic, assignee)
    }

    @Transactional
    fun sync(
        epic: Epic,
        userIds: List<Long>,
    ) {
        val requestedUsers = userRepository.findAllById(userIds)

        epic.assignments
            .filter { it.user !in requestedUsers }
            .map { it.user }
            .forEach { unassignAndNotify(epic, it) }

        requestedUsers
            .filter { user -> epic.assignments.none { it.user == user } }
            .forEach { assignAndNotify(epic, it) }
    }

    @Transactional
    fun ensureAssigned(
        actor: User,
        assigneeId: Long?,
        epic: Epic,
    ) {
        if (assigneeId == null) return
        accessPolicy.requireAnyRole(actor, UserType.ADMIN, UserType.PM, UserType.PO)
        if (!epicRepository.existsByAssignmentsUserIdAndId(assigneeId, epic.requiredId)) {
            val assignee = getUserById(assigneeId)
            assignAndNotify(epic, assignee)
        }
    }

    private fun assignAndNotify(
        epic: Epic,
        assignee: User,
    ) {
        epic.assign(assignee)
        eventPublisher.publishEvent(
            EpicAssignedEvent(
                userId = assignee.requiredId,
                epicId = epic.requiredId,
                epicName = epic.name,
            ),
        )
    }

    private fun unassignAndNotify(
        epic: Epic,
        assignee: User,
    ) {
        epic.unassign(assignee)
        taskRepository.deleteByEpicIdAndAssigneeId(epic.requiredId, assignee.requiredId)
        eventPublisher.publishEvent(
            EpicUnassignedEvent(
                userId = assignee.requiredId,
                epicId = epic.requiredId,
                epicName = epic.name,
            ),
        )
    }

    private fun getEpicById(id: Long): Epic =
        epicRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_EPIC, id)

    private fun getUserById(id: Long): User =
        userRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_USER, id)
}
