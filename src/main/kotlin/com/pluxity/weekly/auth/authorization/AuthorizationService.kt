package com.pluxity.weekly.authorization

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.service.UserService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.task.entity.Task
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class AuthorizationService(
    private val userService: UserService,
    private val projectRepository: ProjectRepository,
    private val epicRepository: EpicRepository,
) {
    fun currentUser(): User {
        val authentication =
            SecurityContextHolder.getContext().authentication
                ?: throw CustomException(ErrorCode.PERMISSION_DENIED)
        return userService.findUserByUsername(authentication.name)
    }

    /** ADMIN만 허용 — 팀 CUD */
    fun requireAdmin(user: User) {
        if (!user.hasRole(UserType.ADMIN)) {
            throw CustomException(ErrorCode.PERMISSION_DENIED)
        }
    }

    /** ADMIN 또는 PM만 허용  */
    fun requireAdminOrPm(user: User) {
        if (user.hasRole(UserType.ADMIN)) return
        if (user.hasRole(UserType.PM)) return
        throw CustomException(ErrorCode.PERMISSION_DENIED)
    }

    /** PM 이 관리하는 프로젝트 ID 목록. ADMIN 이면 null(전체) 반환 */
    fun pmScopedProjectIds(user: User): List<Long>? {
        if (user.hasRole(UserType.ADMIN)) return null
        if (!user.hasRole(UserType.PM)) throw CustomException(ErrorCode.PERMISSION_DENIED)
        return projectRepository.findByPmId(user.requiredId).map { it.requiredId }
    }

    /** ADMIN 또는 해당 프로젝트의 PM만 허용 — 프로젝트 수정/삭제 */
    fun requireProjectManager(
        user: User,
        projectId: Long,
    ) {
        if (user.hasRole(UserType.ADMIN)) return
        if (user.hasRole(UserType.PM) && projectRepository.existsByIdAndPmId(projectId, user.requiredId)) return
        throw CustomException(ErrorCode.PERMISSION_DENIED)
    }

    /** ADMIN, 해당 프로젝트 PM, 또는 에픽에 배정된 사용자 허용 — 에픽 조회, 태스크 생성 */
    fun requireEpicAccess(
        user: User,
        epicId: Long,
    ) {
        if (user.hasRole(UserType.ADMIN)) return
        if (user.hasRole(UserType.PM) && projectRepository.existsByEpicIdAndPmId(epicId, user.requiredId)) return
        if (epicRepository.existsByAssignmentsUserIdAndId(user.requiredId, epicId)) return
        throw CustomException(ErrorCode.PERMISSION_DENIED)
    }

    /** ADMIN 또는 해당 프로젝트의 PM만 허용 — 에픽 생성/수정/삭제 */
    fun requireEpicManage(
        user: User,
        projectId: Long,
    ) {
        if (user.hasRole(UserType.ADMIN)) return
        if (user.hasRole(UserType.PM) && projectRepository.existsByIdAndPmId(projectId, user.requiredId)) return
        throw CustomException(ErrorCode.PERMISSION_DENIED)
    }

    /** ADMIN, 해당 프로젝트 PM,TEAM_LEADER 허용 — 에픽 배정/해제 */
    fun requireEpicAssign(
        user: User,
        epicId: Long,
    ) {
        if (user.hasRole(UserType.ADMIN)) return
        if (user.hasRole(UserType.PM) && projectRepository.existsByEpicIdAndPmId(epicId, user.requiredId)) return
        if (user.hasRole(UserType.TEAM_LEADER)) return
        throw CustomException(ErrorCode.PERMISSION_DENIED)
    }

    /** 본인 태스크(assignee)만 허용 — 태스크 수정/삭제 */
    fun requireTaskOwner(
        user: User,
        task: Task,
    ) {
        if (user.hasRole(UserType.ADMIN)) return
        if (task.assignee?.requiredId != user.requiredId) {
            throw CustomException(ErrorCode.PERMISSION_DENIED)
        }
    }

    /** ADMIN 또는 태스크가 속한 에픽의 프로젝트 PM만 허용 — 태스크 승인/반려 */
    fun requireTaskReviewer(
        user: User,
        task: Task,
    ) {
        if (user.hasRole(UserType.ADMIN)) return
        val pmId = task.epic.project.pmId
        if (user.hasRole(UserType.PM) && pmId != null && pmId == user.requiredId) return
        throw CustomException(ErrorCode.PERMISSION_DENIED)
    }

    /** target + action 조합의 사전 권한 체크 — Chat context 빌드 전 호출 */
    fun checkChatPermission(
        user: User,
        target: String,
        actions: List<String>,
    ) {
        val hasMutation = actions.any { it in listOf("create", "update", "delete", "assign", "unassign") }
        if (!hasMutation) return

        when (target) {
            "project" -> if ("create" in actions) requireAdmin(user) else requireAdminOrPm(user)
            "epic" -> requireAdminOrPm(user)
            "team" -> requireAdmin(user)
        }
    }

    // ── 조회 범위 ──

    /** 사용자가 볼 수 있는 프로젝트 ID. null=전체(Admin). PM+Worker 합집합 */
    fun visibleProjectIds(user: User): List<Long>? {
        if (user.hasRole(UserType.ADMIN)) return null
        val pmProjectIds =
            if (user.hasRole(UserType.PM)) {
                projectRepository.findByPmId(user.requiredId).map { it.requiredId }
            } else {
                emptyList()
            }
        val assignedProjectIds = epicRepository.findByAssignmentsUserId(user.requiredId).map { it.project.requiredId }
        return (pmProjectIds + assignedProjectIds).distinct()
    }

    /** 사용자가 볼 수 있는 에픽 ID. null=전체(Admin). PM+Worker 합집합 */
    fun visibleEpicIds(user: User): List<Long>? {
        if (user.hasRole(UserType.ADMIN)) return null
        val pmEpicIds =
            if (user.hasRole(UserType.PM)) {
                val projectIds = projectRepository.findByPmId(user.requiredId).map { it.requiredId }
                epicRepository.findByProjectIdIn(projectIds).map { it.requiredId }
            } else {
                emptyList()
            }
        val assignedEpicIds = epicRepository.findByAssignmentsUserId(user.requiredId).map { it.requiredId }
        return (pmEpicIds + assignedEpicIds).distinct()
    }

    /** Worker는 본인 태스크만. null=제한없음(Admin/PM) */
    fun restrictedAssigneeId(user: User): Long? {
        if (user.hasRole(UserType.ADMIN)) return null
        if (user.hasRole(UserType.PM)) return null
        return user.requiredId
    }

    fun User.hasRole(userType: UserType): Boolean = userRoles.any { it.role.name.equals(userType.roleName, ignoreCase = true) }
}
