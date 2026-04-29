package com.pluxity.weekly.epic.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
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
    private val authorizationService: AuthorizationService,
    private val assignmentService: EpicAssignmentService,
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
        if (request.status == EpicStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_INITIAL_STATUS, request.status)
        }
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
        request.userIds?.let { assignmentService.sync(epic, it) }
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
        epic.ensureMutable()

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
        request.userIds?.let { assignmentService.sync(epic, it) }
    }

    @Transactional
    fun delete(id: Long) {
        val user = authorizationService.currentUser()
        val epic = getEpicById(id)
        authorizationService.requireEpicManage(user, epic.project.requiredId)
        epicRepository.delete(epic)
    }

    @Transactional
    fun restore(id: Long) {
        val user = authorizationService.currentUser()
        val epic = epicRepository.findRawById(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_EPIC, id)
        authorizationService.requireEpicManage(user, epic.project.requiredId)

        epicRepository.restoreById(id)
        taskRepository.restoreByEpicId(id)
    }

    private fun getEpicById(id: Long): Epic =
        epicRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_EPIC, id)

    private fun getProjectById(id: Long): Project =
        projectRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_PROJECT, id)
}
