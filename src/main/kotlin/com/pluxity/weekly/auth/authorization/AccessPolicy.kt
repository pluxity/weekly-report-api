package com.pluxity.weekly.auth.authorization

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.repository.TeamRepository
import org.springframework.stereotype.Component

/**
 * 리소스 1건에 대한 인가 판정.
 *
 * - 프로젝트: 하위에 내가 배정된 에픽이 있음
 * - 에픽: 배정된 나
 * - 태스크: 담당자 본인
 *
 * 규칙 표는 `docs/notes/authorization-spec.md` 참조.
 */
@Component
class AccessPolicy(
    private val epicRepository: EpicRepository,
    private val projectRepository: ProjectRepository,
    private val teamRepository: TeamRepository,
) {
    // ── 판정 ──

    fun can(
        user: User,
        project: Project,
        action: AccessAction,
    ): Boolean =
        when (action) {
            AccessAction.VIEW -> isManagerOf(user, project) || hasAssignedEpicIn(user, project)
            AccessAction.EDIT, AccessAction.DELETE -> isManagerOf(user, project)
            else -> false
        }

    fun can(
        user: User,
        epic: Epic,
        action: AccessAction,
    ): Boolean =
        when (action) {
            AccessAction.VIEW -> isManagerOf(user, epic) || isAssignedTo(user, epic)
            AccessAction.EDIT, AccessAction.DELETE, AccessAction.ASSIGN -> isManagerOf(user, epic)
            else -> false
        }

    fun can(
        user: User,
        task: Task,
        action: AccessAction,
    ): Boolean =
        when (action) {
            AccessAction.VIEW, AccessAction.EDIT, AccessAction.DELETE -> isManagerOf(user, task) || isAssignee(user, task)
            AccessAction.APPROVE -> isManagerOf(user, task)
            else -> false
        }

    fun can(
        user: User,
        team: Team,
        action: AccessAction,
    ): Boolean =
        when (action) {
            AccessAction.VIEW -> isLeaderOf(user, team)
            else -> false // 팀 수정·삭제·멤버관리는 ADMIN — requireAnyRole 로 판정한다
        }

    // ── 강제 ──

    fun require(
        user: User,
        project: Project,
        action: AccessAction,
    ) {
        if (!can(user, project, action)) deny()
    }

    fun require(
        user: User,
        epic: Epic,
        action: AccessAction,
    ) {
        if (!can(user, epic, action)) deny()
    }

    fun require(
        user: User,
        task: Task,
        action: AccessAction,
    ) {
        if (!can(user, task, action)) deny()
    }

    fun require(
        user: User,
        team: Team,
        action: AccessAction,
    ) {
        if (!can(user, team, action)) deny()
    }

    // ── 생성은 부모에 대해 검사한다 ──

    fun canCreateProject(user: User): Boolean = user.isAdminRole()

    fun canCreateEpic(
        user: User,
        project: Project,
    ): Boolean = isManagerOf(user, project)

    /** 배정된 워커도 본인 업무를 스스로 등록할 수 있으므로 에픽 VIEW 와 같다 */
    fun canCreateTask(
        user: User,
        epic: Epic,
    ): Boolean = can(user, epic, AccessAction.VIEW)

    fun requireCreateProject(user: User) {
        if (!canCreateProject(user)) deny()
    }

    fun requireCreateEpic(
        user: User,
        project: Project,
    ) {
        if (!canCreateEpic(user, project)) deny()
    }

    fun requireCreateTask(
        user: User,
        epic: Epic,
    ) {
        if (!canCreateTask(user, epic)) deny()
    }

    // ── 리소스 없는 역할 판정 ──

    /**
     * 리소스가 특정되지 않은 자리에서 쓰는 거친 판정. 팀 CUD, chat 사전 체크,
     * 주간보고 목록 진입처럼 "어떤 리소스인지 아직 모르는" 지점에만 쓴다.
     */
    fun hasAnyRole(
        user: User,
        vararg types: UserType,
    ): Boolean = types.any { user.hasRole(it) }

    fun requireAnyRole(
        user: User,
        vararg types: UserType,
    ) {
        if (!hasAnyRole(user, *types)) deny()
    }

    // ── 계층 사실 둘 ──

    private fun isManagerOf(
        user: User,
        project: Project,
    ): Boolean = user.isAdminRole() || project.pmId == user.requiredId

    private fun isManagerOf(
        user: User,
        epic: Epic,
    ): Boolean = isManagerOf(user, epic.project)

    private fun isManagerOf(
        user: User,
        task: Task,
    ): Boolean = isManagerOf(user, task.epic)

    private fun isLeaderOf(
        user: User,
        team: Team,
    ): Boolean = user.isAdminRole() || team.leaderId == user.requiredId

    // ── 리소스별 예외 셋 ──

    private fun hasAssignedEpicIn(
        user: User,
        project: Project,
    ): Boolean = epicRepository.existsByAssignmentsUserIdAndProjectId(user.requiredId, project.requiredId)

    private fun isAssignedTo(
        user: User,
        epic: Epic,
    ): Boolean = epicRepository.existsByAssignmentsUserIdAndId(user.requiredId, epic.requiredId)

    private fun isAssignee(
        user: User,
        task: Task,
    ): Boolean = task.assignee?.requiredId == user.requiredId

    // ── 조회 범위 ──
    //
    // 목록 조회는 행마다 can() 을 부를 수 없으므로 같은 규칙을 쿼리 쪽에서도 표현한다.
    // 지금은 ID 목록(null=전체)이라 요청 필터에 주입되는 형태이고, 그래서 호출자가 id 를
    // 직접 넣으면 스코프가 무시되는 구멍이 있다. 다음 리팩토링에서 쿼리 조건 반환으로 바꾼다.
    // docs/notes/authorization-spec.md "나중에 볼 것" 참조.

    /** 사용자가 볼 수 있는 프로젝트 ID. null=전체(Admin). PM+Worker 합집합 */
    fun visibleProjectIds(user: User): List<Long>? {
        if (user.isAdminRole()) return null
        val pmProjectIds =
            if (user.isProjectManager()) {
                projectRepository.findByPmId(user.requiredId).map { it.requiredId }
            } else {
                emptyList()
            }
        val assignedProjectIds = epicRepository.findByAssignmentsUserId(user.requiredId).map { it.project.requiredId }
        return (pmProjectIds + assignedProjectIds).distinct()
    }

    /** 사용자가 볼 수 있는 에픽 ID. null=전체(Admin). PM/PO+Worker 합집합 */
    fun visibleEpicIds(user: User): List<Long>? {
        if (user.isAdminRole()) return null
        val pmEpicIds =
            if (user.isProjectManager()) {
                val projectIds = projectRepository.findByPmId(user.requiredId).map { it.requiredId }
                epicRepository.findByProjectIdIn(projectIds).map { it.requiredId }
            } else {
                emptyList()
            }
        val assignedEpicIds = epicRepository.findByAssignmentsUserId(user.requiredId).map { it.requiredId }
        return (pmEpicIds + assignedEpicIds).distinct()
    }

    /** Worker는 본인 태스크만. null=제한없음(Admin/PM/PO) */
    fun restrictedAssigneeId(user: User): Long? {
        if (user.isAdminRole()) return null
        if (user.isProjectManager()) return null
        return user.requiredId
    }

    /** PM 이 관리하는 프로젝트 ID 목록. ADMIN 이면 null(전체). PM 이 아니면 거부 */
    fun pmScopedProjectIds(user: User): List<Long>? {
        if (user.isAdminRole()) return null
        if (!user.isProjectManager()) deny()
        return projectRepository.findByPmId(user.requiredId).map { it.requiredId }
    }

    /** 사용자가 볼 수 있는 팀 ID. null=전체(Admin), Leader는 본인이 leader인 팀들 */
    fun visibleTeamIds(user: User): List<Long>? {
        if (user.isAdminRole()) return null
        return teamRepository.findByLeaderId(user.requiredId).map { it.requiredId }
    }

    // ── 역할 조회 ──

    private fun User.hasRole(type: UserType): Boolean = userRoles.any { it.role.name.equals(type.roleName, ignoreCase = true) }

    private fun User.isProjectManager(): Boolean = hasRole(UserType.PM) || hasRole(UserType.PO)

    private fun User.isAdminRole(): Boolean = hasRole(UserType.ADMIN)

    private fun deny(): Nothing = throw CustomException(ErrorCode.PERMISSION_DENIED)
}
