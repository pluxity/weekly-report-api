package com.pluxity.weekly.project.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.dto.ProjectRequest
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.project.dto.ProjectUpdateRequest
import com.pluxity.weekly.project.dto.toResponse
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.event.ProjectPmAssignedEvent
import com.pluxity.weekly.project.repository.ProjectRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val epicRepository: EpicRepository,
    private val userRepository: UserRepository,
    private val authorizationService: AuthorizationService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun findAll(): List<ProjectResponse> = search(ProjectSearchFilter())

    fun search(filter: ProjectSearchFilter): List<ProjectResponse> {
        val user = authorizationService.currentUser()
        val scoped = filter.copy(projectIds = filter.projectIds ?: authorizationService.visibleProjectIds(user))
        if (scoped.projectIds?.isEmpty() == true) return emptyList()

        val projects = projectRepository.findByFilter(scoped)
        if (projects.isEmpty()) return emptyList()

        val memberMap =
            projectRepository
                .findMembersByProjectIds(projects.map { it.requiredId })
                .groupBy { it.projectId }
        val pmNameMap = resolvePmNames(projects.mapNotNull { it.pmId })
        return projects.map { it.toResponse(memberMap[it.requiredId].orEmpty(), pmNameMap[it.pmId]) }
    }

    fun findById(id: Long): ProjectResponse {
        val project = getById(id)
        val pmName = project.pmId?.let { userRepository.findByIdOrNull(it)?.name }
        return project.toResponse(projectRepository.findMembersByProjectIds(listOf(project.requiredId)), pmName)
    }

    @Transactional
    fun create(request: ProjectRequest): Long {
        val user = authorizationService.currentUser()
        authorizationService.requireAdmin(user)
        if (request.status == ProjectStatus.DONE) {
            throw CustomException(ErrorCode.INVALID_INITIAL_STATUS, request.status)
        }
        request.pmId?.let { ensurePmExists(it) }
        val project =
            projectRepository
                .save(
                    Project(
                        name = request.name,
                        description = request.description,
                        status = request.status,
                        startDate = request.startDate,
                        dueDate = request.dueDate,
                        pmId = request.pmId,
                    ),
                )
        request.pmId?.let { publishPmAssigned(it, project) }
        return project.requiredId
    }

    @Transactional
    fun update(
        id: Long,
        request: ProjectUpdateRequest,
    ) {
        val user = authorizationService.currentUser()
        authorizationService.requireProjectManager(user, id)
        val project = getById(id)
        project.ensureMutable()
        request.pmId?.let { ensurePmExists(it) }
        val previousPmId = project.pmId

        request.status?.let { newStatus ->
            val allEpicsDone =
                if (newStatus == ProjectStatus.DONE) {
                    epicRepository.findByProjectIdIn(listOf(id)).let { epics ->
                        epics.isNotEmpty() && epics.all { it.status == EpicStatus.DONE }
                    }
                } else {
                    false
                }
            project.changeStatus(newStatus, allEpicsDone)
        }

        project.update(
            name = request.name,
            description = request.description,
            startDate = request.startDate,
            dueDate = request.dueDate,
            pmId = request.pmId,
        )

        request.pmId
            ?.takeIf { it != previousPmId }
            ?.let { publishPmAssigned(it, project) }
    }

    @Transactional
    fun delete(id: Long) {
        val user = authorizationService.currentUser()
        authorizationService.requireProjectManager(user, id)
        projectRepository.delete(getById(id))
    }

    private fun getById(id: Long): Project =
        projectRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_PROJECT, id)

    private fun ensurePmExists(pmId: Long) {
        if (!userRepository.existsById(pmId)) {
            throw CustomException(ErrorCode.NOT_FOUND_USER, pmId)
        }
    }

    private fun publishPmAssigned(
        pmId: Long,
        project: Project,
    ) {
        eventPublisher.publishEvent(
            ProjectPmAssignedEvent(
                pmId = pmId,
                projectId = project.requiredId,
                projectName = project.name,
            ),
        )
    }

    private fun resolvePmNames(pmIds: List<Long>): Map<Long, String> =
        if (pmIds.isEmpty()) {
            emptyMap()
        } else {
            userRepository.findAllById(pmIds.distinct()).associate { it.requiredId to it.name }
        }
}
