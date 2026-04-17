package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.Candidate
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.SelectField
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.repository.TaskRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class SelectFieldResolver(
    private val projectRepository: ProjectRepository,
    private val epicRepository: EpicRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
    private val projectService: ProjectService,
    private val epicService: EpicService,
) {
    fun resolve(action: LlmAction): List<SelectField> {
        val missingFields = action.missingFields ?: emptyList()
        val candidateIds = action.candidates ?: emptyList()
        val result =
            missingFields
                .mapNotNull { dispatch(it, action.target, candidateIds) }
                .toMutableList()

        addSelectFields(action, result)

        return result
    }

    fun resolveCandidateNames(
        field: String,
        target: String?,
        candidateIds: List<Long>,
    ): List<String> = dispatch(field, target, candidateIds)?.candidates?.map { it.name } ?: emptyList()

    private fun dispatch(
        field: String,
        target: String?,
        candidateIds: List<Long>,
    ): SelectField? =
        when (field) {
            "id" -> resolveIdCandidates(target, candidateIds)
            "project_id" -> resolveProjectCandidates(candidateIds)
            "epic_id" -> resolveEpicCandidates(candidateIds)
            // user_ids / remove_user_ids: 프롬프트는 candidates(전체 사용자 ID)를 담아 보내지만
            // 후보가 많아 드롭다운 UX 가 부적합해 의도적으로 선택지를 만들지 않는다.
            // (unassign 의 경우 "해당 에픽 배정자"로 좁히면 적합하므로 추후 분기 추가 여지)
            else -> null
        }

    private fun resolveIdCandidates(
        target: String?,
        candidateIds: List<Long>,
    ): SelectField? {
        if (candidateIds.isEmpty()) return null
        val candidates =
            when (target) {
                "task" ->
                    taskRepository.findAllWithEpicAndProjectByIdIn(candidateIds).map { task ->
                        Candidate(task.requiredId.toString(), "${task.name} (${task.epic.project.name}/${task.epic.name})")
                    }
                "epic" ->
                    epicRepository.findAllWithProjectByIdIn(candidateIds).map { epic ->
                        Candidate(epic.requiredId.toString(), "${epic.name} (${epic.project.name})")
                    }
                "project" ->
                    projectRepository.findAllById(candidateIds).map { Candidate(it.requiredId.toString(), it.name) }
                else -> return null
            }
        return SelectField(field = "id", candidates = candidates)
    }

    private fun resolveProjectCandidates(candidateIds: List<Long>): SelectField? {
        val candidates =
            if (candidateIds.isNotEmpty()) {
                projectRepository.findAllById(candidateIds).map { Candidate(it.requiredId.toString(), it.name) }
            } else {
                projectService.findAll().map { Candidate(it.id.toString(), it.name) }
            }
        if (candidates.isEmpty()) return null
        return SelectField(field = "projectId", candidates = candidates)
    }

    private fun resolveEpicCandidates(candidateIds: List<Long>): SelectField? {
        val candidates =
            if (candidateIds.isNotEmpty()) {
                epicRepository.findAllWithProjectByIdIn(candidateIds).map { epic ->
                    Candidate(epic.requiredId.toString(), "${epic.name} (${epic.project.name})")
                }
            } else {
                epicService.findAll().map { Candidate(it.id.toString(), it.name) }
            }
        if (candidates.isEmpty()) return null
        return SelectField(field = "epicId", candidates = candidates)
    }

    private fun resolveUserCandidates(
        field: String,
        roleName: String? = null,
    ): SelectField {
        val users = userRepository.findAllBy(Sort.by("name"))
        val candidates =
            if (roleName != null) {
                users.filter { user -> user.userRoles.any { it.role.name.uppercase() == roleName } }
            } else {
                users
            }.map { Candidate(it.requiredId.toString(), it.name) }
        return SelectField(field = field, candidates = candidates)
    }

    private fun resolveStatusCandidates(): SelectField {
        val statuses = listOf("TODO", "IN_PROGRESS")
        return SelectField(
            field = "status",
            candidates = statuses.map { Candidate(it, it) },
        )
    }

    private fun addSelectFields(
        action: LlmAction,
        result: MutableList<SelectField>,
    ) {
        if (action.action !in listOf("create", "update")) return

        val existingFields = result.map { it.field }.toSet()

        when (action.target) {
            "project" -> {
                if ("pmId" !in existingFields) {
                    result.add(resolveUserCandidates("pmId", "PM"))
                }
                if ("status" !in existingFields) {
                    result.add(resolveStatusCandidates())
                }
            }
            "epic" -> {
                if ("projectId" !in existingFields) {
                    result.add(resolveProjectCandidates(emptyList()) ?: return)
                }
                if ("userIds" !in existingFields) {
                    result.add(resolveUserCandidates("userIds"))
                }
                if ("status" !in existingFields) {
                    result.add(resolveStatusCandidates())
                }
            }
            "task" -> {
                if ("epicId" !in existingFields) {
                    result.add(resolveEpicCandidates(emptyList()) ?: return)
                }
            }
        }
    }
}
