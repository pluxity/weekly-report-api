package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.dto.TeamSearchFilter
import com.pluxity.weekly.chat.util.ChatScope
import com.pluxity.weekly.chat.v2.dto.SearchItemsArgs
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.dto.TeamResponse
import com.pluxity.weekly.team.service.TeamService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

/**
 * search_items 실행부 — 태스크·업무 그룹·프로젝트·팀 통합 검색.
 * 사용자가 타입을 안 붙이고 이름만 말하는 실사용 패턴에 맞춰 3계층을 한 번에 뒤진다 (팀은 명시 시에만).
 * 이름 매칭은 in-memory 토큰 매칭([ItemNameMatcher]) — "cctv API" ↔ "CCTV 목록 API".
 * 결과는 타입당 limit건으로 캡하되 totals에 전체 개수를 담아 "잘렸는지"를 모델이 알게 한다.
 */
@Component
class SearchItemsHandler(
    private val taskService: TaskService,
    private val epicService: EpicService,
    private val projectService: ProjectService,
    private val teamService: TeamService,
    private val userRepository: UserRepository,
    private val support: ChatV2ToolSupport,
    private val objectMapper: ObjectMapper,
) {
    fun handle(
        argumentsJson: String,
        currentUserId: Long,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = support.readArgs<SearchItemsArgs>(argumentsJson)
        val query = args.query?.trim()?.takeIf { it.isNotBlank() }
        // type 미지정(전체 검색)과 잘못된 type을 구분: 원본이 null이면 미지정. 값이 있는데 파싱 실패면
        // 명백한 실수이므로 조용히 빈 결과가 아니라 error로 돌려보내 모델이 자가교정하게 한다 (get_item_details와 일관).
        val typeOmitted = args.type == null
        val type = ChatV2EntityType.from(args.type)
        // USER는 search_items 대상이 아니다 (사용자는 search_users) — null(미인식)과 함께 거부
        if (!typeOmitted && (type == null || type == ChatV2EntityType.USER)) {
            return support.errorResult("알 수 없는 종류: ${args.type} (task/epic/project/team만 가능)")
        }
        if (typeOmitted && query == null && !args.hasFilter()) {
            return support.errorResult("query 또는 필터(type/status/assignee_me/assignee/project/epic/기간)를 하나 이상 지정하세요.")
        }
        // 부모·담당자 필터는 이름 → 서버가 id로 해소 (모델이 id를 지어낼 여지 제거). 0건/다건이면 안내로 되돌린다.
        val scopeStart = ChatScope.scopeStartDate()
        val assigneeId =
            when (val r = support.resolveByName(args.assignee, "사용자", ChatV2EntityType.USER, idRegistry) {
                userRepository.findAllBy(Sort.by("name")).map { it.requiredId to it.name }
            }) {
                is NameResolution.Error -> return support.errorResult(r.message)
                is NameResolution.Resolved -> r.id
                NameResolution.NotRequested -> null
            }
        val projectId =
            when (val r = support.resolveByName(args.project, "프로젝트", ChatV2EntityType.PROJECT, idRegistry) {
                projectService.search(ProjectSearchFilter(scopeStartDate = scopeStart)).map { it.id to it.name }
            }) {
                is NameResolution.Error -> return support.errorResult(r.message)
                is NameResolution.Resolved -> r.id
                NameResolution.NotRequested -> null
            }
        val epicId =
            when (val r = support.resolveByName(args.epic, "업무 그룹", ChatV2EntityType.EPIC, idRegistry) {
                epicService.search(EpicSearchFilter(scopeStartDate = scopeStart)).map { it.id to it.name }
            }) {
                is NameResolution.Error -> return support.errorResult(r.message)
                is NameResolution.Resolved -> r.id
                NameResolution.NotRequested -> null
            }
        // PM은 프로젝트 전용 필터 — assignee와 같은 이름→id 해소 (모델은 id를 모름)
        val pmId =
            when (val r = support.resolveByName(args.pm, "사용자", ChatV2EntityType.USER, idRegistry) {
                userRepository.findAllBy(Sort.by("name")).map { it.requiredId to it.name }
            }) {
                is NameResolution.Error -> return support.errorResult(r.message)
                is NameResolution.Resolved -> r.id
                NameResolution.NotRequested -> null
            }

        val limit = (args.limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

        // type=team → 팀 객체(구성원 포함) 반환. "우리 팀원 누구" 류. team_me=내가 리더인 팀, team=이름, 없으면 query.
        if (type == ChatV2EntityType.TEAM) return searchTeams(args, query, currentUserId, limit, idRegistry)

        // 팀 스코프(태스크) — team_me/team이면 "팀 멤버가 담당한 태스크"로 좁힌다. Task엔 team 참조가 없고
        // (프로젝트도 팀을 가로지름) 유일한 경로가 멤버 담당이라, 팀 → 멤버 userId → assignee IN 으로 해소한다.
        val teamMemberIds: List<Long>? =
            when {
                args.teamMe == true -> {
                    val led = myLedTeams(currentUserId)
                    if (led.isEmpty()) return support.errorResult("리더로 있는 팀이 없어 '우리 팀' 태스크를 조회할 수 없어요.")
                    led.flatMap { it.members.map { m -> m.id } }.distinct()
                }
                args.team != null ->
                    when (val r = support.resolveByName(args.team, "팀", ChatV2EntityType.TEAM, idRegistry) {
                        teamService.search(TeamSearchFilter()).map { it.id to it.name }
                    }) {
                        is NameResolution.Error -> return support.errorResult(r.message)
                        is NameResolution.Resolved -> teamService.findMembers(r.id).map { m -> m.id }
                        NameResolution.NotRequested -> null
                    }
                else -> null
            }
        // 팀은 확정됐는데 멤버가 없으면 태스크 0건 — assignee IN () 로 새지 않게 명시적 빈 결과
        if (teamMemberIds != null && teamMemberIds.isEmpty()) {
            return buildResponse(emptyList(), emptyList(), emptyList(), args.sort, args.order, limit, idRegistry)
        }

        val c = Criteria.from(args, query, currentUserId, assigneeId, projectId, epicId, pmId, teamMemberIds)
        // 소속/담당 필터가 있으면 의미 없는 계층은 건너뛴다
        // (epic_id → 태스크만, 담당자 필터 → 프로젝트 제외, PM 필터 → 프로젝트만, 완료일 필터 → 태스크만[완료일은 Task 전용 컬럼])
        val taskMatches = if ((typeOmitted || type == ChatV2EntityType.TASK) && c.pmId == null) searchTasks(c) else emptyList()
        val epicMatches =
            if ((typeOmitted || type == ChatV2EntityType.EPIC) && c.epicId == null && c.pmId == null &&
                !c.hasCompletedFilter && !c.hasTeamFilter
            ) {
                searchEpics(c)
            } else {
                emptyList()
            }
        val projectMatches =
            if ((typeOmitted || type == ChatV2EntityType.PROJECT) && c.epicId == null && c.assigneeId == null &&
                !c.hasCompletedFilter && !c.hasTeamFilter
            ) {
                searchProjects(c)
            } else {
                emptyList()
            }

        return buildResponse(taskMatches, epicMatches, projectMatches, args.sort, args.order, limit, idRegistry)
    }

    // ── 계층별 검색 ──
    // status를 각 계층 enum으로 파싱하고, 준 status가 그 계층에 없는 값이면 빈 결과(예: IN_REVIEW를 epic에). 이후 이름 매칭.

    private fun searchTasks(c: Criteria): List<TaskResponse> {
        val status = TaskStatus.entries.find { it.name.equals(c.statusRaw, ignoreCase = true) }
        if (c.statusRaw != null && status == null) return emptyList()
        return taskService
            .search(
                TaskSearchFilter(
                    status = status,
                    epicId = c.epicId,
                    projectId = c.projectId,
                    assigneeId = c.assigneeId,
                    assigneeIds = c.assigneeIds,
                    dueDateFrom = c.dueFrom,
                    dueDateTo = c.dueTo,
                    completedFrom = c.completedFrom,
                    completedTo = c.completedTo,
                    excludeDone = c.excludeDone,
                    scopeStartDate = c.scopeStart,
                ),
            ).matching(c.query) { it.name }
    }

    private fun searchEpics(c: Criteria): List<EpicResponse> {
        val status = EpicStatus.entries.find { it.name.equals(c.statusRaw, ignoreCase = true) }
        if (c.statusRaw != null && status == null) return emptyList()
        return epicService
            .search(
                EpicSearchFilter(
                    status = status,
                    projectId = c.projectId,
                    assigneeId = c.assigneeId,
                    dueDateFrom = c.dueFrom,
                    dueDateTo = c.dueTo,
                    excludeDone = c.excludeDone,
                    scopeStartDate = c.scopeStart,
                ),
            ).matching(c.query) { it.name }
    }

    private fun searchProjects(c: Criteria): List<ProjectResponse> {
        val status = ProjectStatus.entries.find { it.name.equals(c.statusRaw, ignoreCase = true) }
        if (c.statusRaw != null && status == null) return emptyList()
        return projectService
            .search(
                ProjectSearchFilter(
                    status = status,
                    pmId = c.pmId,
                    dueDateFrom = c.dueFrom,
                    dueDateTo = c.dueTo,
                    excludeDone = c.excludeDone,
                    scopeStartDate = c.scopeStart,
                ),
            ).matching(c.query) { it.name }
    }

    /**
     * "우리 팀" = 현재 유저가 리더인 팀들. search가 멤버·리더명을 채우므로(findById는 멤버를 비운다)
     * 팀 멤버 조회(type=team)와 태스크 스코프(assignee IN) 둘 다 이걸로 해소한다.
     */
    private fun myLedTeams(currentUserId: Long): List<TeamResponse> =
        teamService.search(TeamSearchFilter()).filter { it.leaderId == currentUserId }

    private fun searchTeams(
        args: SearchItemsArgs,
        query: String?,
        currentUserId: Long,
        limit: Int,
        idRegistry: ChatV2IdRegistry,
    ): String {
        // team_me="우리 팀"(내가 리더인 팀), team=이름 매칭, 둘 다 없으면 query로 전체 검색. 결과 팀은 teamMap이 구성원까지 담는다.
        val matched: List<TeamResponse> =
            when {
                args.teamMe == true -> {
                    val led = myLedTeams(currentUserId)
                    if (led.isEmpty()) return support.errorResult("리더로 있는 팀이 없어요.")
                    led
                }
                args.team != null -> teamService.search(TeamSearchFilter()).matching(args.team) { it.name }
                else -> teamService.search(TeamSearchFilter()).matching(query) { it.name }
            }
        val teams =
            matched.take(limit)
                .onEach { idRegistry.register(ChatV2EntityType.TEAM, it.id) }
                .map(support::teamMap)
        return objectMapper.writeValueAsString(
            mapOf("teams" to teams, "totals" to mapOf("teams" to matched.size), "truncated" to (matched.size > limit)),
        )
    }

    // ── 응답 조립 ──

    private fun buildResponse(
        taskMatches: List<TaskResponse>,
        epicMatches: List<EpicResponse>,
        projectMatches: List<ProjectResponse>,
        sort: String?,
        order: String?,
        limit: Int,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val tasks =
            sortTasks(taskMatches, sort, order).take(limit)
                .onEach { idRegistry.register(ChatV2EntityType.TASK, it.id) }.map(support::taskMap)
        val epics =
            sortEpics(epicMatches, sort, order).take(limit)
                .onEach { idRegistry.register(ChatV2EntityType.EPIC, it.id) }.map(support::epicMap)
        val projects =
            sortProjects(projectMatches, sort, order).take(limit)
                .onEach { idRegistry.register(ChatV2EntityType.PROJECT, it.id) }.map(support::projectMap)

        return objectMapper.writeValueAsString(
            mapOf(
                "tasks" to tasks,
                "epics" to epics,
                "projects" to projects,
                "totals" to mapOf("tasks" to taskMatches.size, "epics" to epicMatches.size, "projects" to projectMatches.size),
                "truncated" to (taskMatches.size > limit || epicMatches.size > limit || projectMatches.size > limit),
            ),
        )
    }

    private data class Criteria(
        val query: String?,
        val statusRaw: String?,
        val assigneeId: Long?,
        val projectId: Long?,
        val epicId: Long?,
        val pmId: Long?,
        val assigneeIds: List<Long>?,
        val dueFrom: LocalDate?,
        val dueTo: LocalDate?,
        val completedFrom: LocalDate?,
        val completedTo: LocalDate?,
        val excludeDone: Boolean,
        val scopeStart: LocalDate,
    ) {
        val hasCompletedFilter: Boolean get() = completedFrom != null || completedTo != null
        val hasTeamFilter: Boolean get() = assigneeIds != null

        companion object {
            fun from(
                args: SearchItemsArgs,
                query: String?,
                currentUserId: Long,
                assigneeId: Long?,
                projectId: Long?,
                epicId: Long?,
                pmId: Long?,
                assigneeIds: List<Long>?,
            ) = Criteria(
                query = query,
                statusRaw = args.status,
                assigneeId = assigneeId ?: if (args.assigneeMe == true) currentUserId else null,
                projectId = projectId,
                epicId = epicId,
                pmId = pmId ?: if (args.pmMe == true) currentUserId else null,
                assigneeIds = assigneeIds,
                dueFrom = args.dueDateFrom?.let(LocalDate::parse),
                dueTo = args.dueDateTo?.let(LocalDate::parse),
                completedFrom = args.completedFrom?.let(LocalDate::parse),
                completedTo = args.completedTo?.let(LocalDate::parse),
                excludeDone = args.excludeDone ?: false,
                scopeStart = ChatScope.scopeStartDate(),
            )
        }
    }

    // ── 정렬 ──

    private fun <T, R : Comparable<R>> List<T>.sortedByField(
        order: String?,
        selector: (T) -> R?,
    ): List<T> {
        val comparator = compareBy(nullsLast(naturalOrder<R>()), selector)
        return sortedWith(if (order == "desc") comparator.reversed() else comparator)
    }

    private fun sortTasks(
        tasks: List<TaskResponse>,
        sort: String?,
        order: String?,
    ): List<TaskResponse> =
        when (sort) {
            "due_date" -> tasks.sortedByField(order) { it.dueDate }
            "progress" -> tasks.sortedByField(order) { it.progress }
            "name" -> tasks.sortedByField(order) { it.name }
            else -> tasks
        }

    private fun sortEpics(
        epics: List<EpicResponse>,
        sort: String?,
        order: String?,
    ): List<EpicResponse> =
        when (sort) {
            "due_date" -> epics.sortedByField(order) { it.dueDate }
            "name" -> epics.sortedByField(order) { it.name }
            else -> epics
        }

    private fun sortProjects(
        projects: List<ProjectResponse>,
        sort: String?,
        order: String?,
    ): List<ProjectResponse> =
        when (sort) {
            "due_date" -> projects.sortedByField(order) { it.dueDate }
            "progress" -> projects.sortedByField(order) { it.progress }
            "name" -> projects.sortedByField(order) { it.name }
            else -> projects
        }

    companion object {
        private const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 30
    }
}

/** search_items 인자에 필터가 하나라도 있는지 */
private fun SearchItemsArgs.hasFilter(): Boolean =
    status != null || assigneeMe == true || assignee != null || pmMe == true || pm != null ||
        teamMe == true || team != null || project != null ||
        epic != null || dueDateFrom != null || dueDateTo != null || completedFrom != null || completedTo != null ||
        excludeDone == true

/** query 이름 토큰 매칭 필터 — query 없으면 전체 통과 */
private inline fun <T> List<T>.matching(
    query: String?,
    name: (T) -> String,
): List<T> = filter { query == null || ItemNameMatcher.matches(query, name(it)) }
