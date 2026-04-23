package com.pluxity.weekly.chat.service

import com.pluxity.weekly.auth.authorization.UserType
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.Candidate
import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.dto.LlmAction
import com.pluxity.weekly.chat.dto.SelectField
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.epic.service.EpicAssignmentService
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
    private val epicAssignmentService: EpicAssignmentService,
) {
    fun resolve(action: LlmAction): List<SelectField> {
        val missingFields = action.missingFields ?: emptyList()
        val result =
            missingFields
                .mapNotNull { dispatch(it, action) }
                .toMutableList()

        addSelectFields(action, result)

        return result
    }

    fun resolveCandidates(
        field: String,
        action: LlmAction,
    ): List<Candidate> = dispatch(field, action)?.candidates ?: emptyList()

    private fun dispatch(
        field: String,
        action: LlmAction,
    ): SelectField? {
        val target = ChatTarget.fromOrNull(action.target)
        val candidateIds = action.candidates ?: emptyList()
        return when (field) {
            "id" -> resolveIdCandidates(target, candidateIds)
            "project_id" -> resolveProjectCandidates(candidateIds)
            "epic_id" -> resolveEpicCandidates(candidateIds)
            "user_ids" -> resolveUserCandidates("userIds")
            "remove_user_ids" -> resolveRemoveUserCandidates(action)
            else -> null
        }
    }

    private fun resolveIdCandidates(
        target: ChatTarget?,
        candidateIds: List<Long>,
    ): SelectField? {
        if (candidateIds.isEmpty()) return null
        val candidates =
            when (target) {
                ChatTarget.TASK ->
                    taskRepository.findAllWithEpicAndProjectByIdIn(candidateIds).map { task ->
                        Candidate(task.requiredId.toString(), "${task.name} (${task.epic.project.name}/${task.epic.name})")
                    }
                ChatTarget.EPIC ->
                    epicRepository.findAllWithProjectByIdIn(candidateIds).map { epic ->
                        Candidate(epic.requiredId.toString(), "${epic.name} (${epic.project.name})")
                    }
                ChatTarget.PROJECT ->
                    projectRepository.findAllById(candidateIds).map { Candidate(it.requiredId.toString(), it.name) }
                ChatTarget.TEAM, ChatTarget.REVIEW, null -> return null
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

    private fun resolveRemoveUserCandidates(action: LlmAction): SelectField? {
        if (ChatTarget.fromOrNull(action.target) != ChatTarget.EPIC || action.id == null) return null
        val assigneeIds = epicAssignmentService.findByEpic(action.id).map { it.userId }
        if (assigneeIds.isEmpty()) return null
        val candidates =
            userRepository
                .findAllById(assigneeIds)
                .distinctBy { it.requiredId }
                .map { Candidate(it.requiredId.toString(), it.name) }
        if (candidates.isEmpty()) return null
        return SelectField(field = "removeUserIds", candidates = candidates)
    }

    private fun resolveUserCandidates(
        field: String,
        roleNames: List<String>? = null,
    ): SelectField {
        val users = userRepository.findAllBy(Sort.by("name"))
        val filtered =
            if (roleNames != null) {
                users.filter { user -> user.userRoles.any { it.role.name.uppercase() in roleNames } }
            } else {
                users.filterNot { user -> user.userRoles.any { it.role.name.equals(UserType.ADMIN.name, ignoreCase = true) } }
            }
        val candidates =
            filtered
                .distinctBy { it.requiredId }
                .map { Candidate(it.requiredId.toString(), it.name) }
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
        val type = ChatActionType.fromOrNull(action.action)
        if (type != ChatActionType.CREATE && type != ChatActionType.UPDATE) return

        val existingFields = result.map { it.field }.toSet()

        when (ChatTarget.fromOrNull(action.target)) {
            ChatTarget.PROJECT -> {
                if ("pmId" !in existingFields) {
                    result.add(resolveUserCandidates("pmId", listOf("PM", "PO")))
                }
                if ("status" !in existingFields) {
                    result.add(resolveStatusCandidates())
                }
            }
            ChatTarget.EPIC -> {
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
            ChatTarget.TASK -> {
                if ("epicId" !in existingFields) {
                    result.add(resolveEpicCandidates(emptyList()) ?: return)
                }
            }
            ChatTarget.TEAM, ChatTarget.REVIEW, null -> Unit
        }
    }
}
