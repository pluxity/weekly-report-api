package com.pluxity.weekly.epic.service

import com.pluxity.weekly.auth.authorization.AccessAction
import com.pluxity.weekly.auth.authorization.AccessPolicy
import com.pluxity.weekly.auth.authorization.CurrentUserProvider
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
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
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EpicService(
    private val epicRepository: EpicRepository,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val accessPolicy: AccessPolicy,
    private val assignmentService: EpicAssignmentService,
) {
    fun findAll(): List<EpicResponse> = search(EpicSearchFilter())

    fun search(filter: EpicSearchFilter): List<EpicResponse> {
        val user = currentUserProvider.get()
        val scoped = filter.copy(epicIds = filter.epicIds ?: accessPolicy.visibleEpicIds(user))
        if (scoped.epicIds?.isEmpty() == true) return emptyList()
        val epics = epicRepository.findByFilter(scoped)
        if (epics.isEmpty()) return emptyList()
        val tasksByEpicId = taskRepository.findByEpicIn(epics).groupBy { it.epic.requiredId }
        return epics.map { it.toResponse(completedAt = it.derivedCompletedAt(tasksByEpicId[it.requiredId].orEmpty())) }
    }

    fun findById(id: Long): EpicResponse {
        val user = currentUserProvider.get()
        val epic = getEpicById(id)
        accessPolicy.require(user, epic, AccessAction.VIEW)
        return epic.toResponse()
    }

    @Transactional
    fun create(request: EpicRequest): Long {
        val user = currentUserProvider.get()
        val project = getProjectById(request.projectId)
        accessPolicy.requireCreateEpic(user, project)
        if (request.status !in EpicStatus.initStates) {
            throw CustomException(ErrorCode.INVALID_INITIAL_STATUS, request.status)
        }
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
        request.userIds?.let { assignmentService.sync(epic, it) }
        return epic.requiredId
    }

    @Transactional
    fun update(
        id: Long,
        request: EpicUpdateRequest,
    ) {
        val user = currentUserProvider.get()
        val epic = getEpicById(id)
        accessPolicy.require(user, epic, AccessAction.EDIT)
        epic.ensureMutable()

        request.status?.let { newStatus ->
            val allTasksDone =
                if (newStatus == EpicStatus.DONE) {
                    taskRepository.findByEpicId(id).all { it.status == TaskStatus.DONE }
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
        request.userIds?.let { assignmentService.sync(epic, it) }
    }

    @Transactional
    fun delete(id: Long) {
        val user = currentUserProvider.get()
        val epic = getEpicById(id)
        accessPolicy.require(user, epic, AccessAction.DELETE)
        epicRepository.delete(epic)
    }

    @Transactional
    fun restore(id: Long): EpicResponse {
        val user = currentUserProvider.get()
        val projectId =
            epicRepository.findProjectIdRawById(id)
                ?: throw CustomException(ErrorCode.NOT_FOUND_EPIC, id)
        if (epicRepository.isParentProjectDeletedByEpicId(id)) {
            throw CustomException(ErrorCode.PARENT_PROJECT_DELETED)
        }
        accessPolicy.require(user, getProjectById(projectId), AccessAction.EDIT)

        epicRepository.restoreById(id)
        taskRepository.restoreByEpicId(id)

        // 복구 직후라 권한은 위에서 이미 확인했다. findById 를 다시 타면 사용자 조회가 한 번 더 돈다
        return getEpicById(id).toResponse()
    }

    private fun getEpicById(id: Long): Epic =
        epicRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_EPIC, id)

    private fun getProjectById(id: Long): Project =
        projectRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_PROJECT, id)
}
