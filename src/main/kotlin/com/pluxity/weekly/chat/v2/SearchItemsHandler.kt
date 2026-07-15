package com.pluxity.weekly.chat.v2

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
import com.pluxity.weekly.team.service.TeamService
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
        val type = SearchType.from(args.type)
        if (!typeOmitted && type == null) {
            return support.errorResult("알 수 없는 종류: ${args.type} (task/epic/project/team만 가능)")
        }
        if (typeOmitted && query == null && !args.hasFilter()) {
            return support.errorResult("query 또는 필터(type/status/assignee_me/assignee_id/기간)를 하나 이상 지정하세요.")
        }
        // 검색 필터의 id도 검색으로 확인된 값만 — 지어낸 id로 빈 결과가 나가면 "없다"는 거짓 부정으로 이어진다
        // (실사례: 알파 프로젝트=10인데 project_id=3으로 검색 → "알파에는 없습니다")
        support.validateKnown(idRegistry, ChatV2IdRegistry.USER, args.assigneeId, "assignee_id")?.let { return it }
        support.validateKnown(idRegistry, ChatV2IdRegistry.PROJECT, args.projectId, "project_id")?.let { return it }
        support.validateKnown(idRegistry, ChatV2IdRegistry.EPIC, args.epicId, "epic_id")?.let { return it }

        val limit = (args.limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

        if (type == SearchType.TEAM) return searchTeams(query, limit)

        val c = Criteria.from(args, query, currentUserId)
        // 소속/담당 필터가 있으면 의미 없는 계층은 건너뛴다 (epic_id → 태스크만, 담당자 필터 → 프로젝트 제외)
        val taskMatches = if (typeOmitted || type == SearchType.TASK) searchTasks(c) else emptyList()
        val epicMatches = if ((typeOmitted || type == SearchType.EPIC) && c.epicId == null) searchEpics(c) else emptyList()
        val projectMatches =
            if ((typeOmitted || type == SearchType.PROJECT) && c.epicId == null && c.assigneeId == null) {
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
                    dueDateFrom = c.dueFrom,
                    dueDateTo = c.dueTo,
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
                    dueDateFrom = c.dueFrom,
                    dueDateTo = c.dueTo,
                    excludeDone = c.excludeDone,
                    scopeStartDate = c.scopeStart,
                ),
            ).matching(c.query) { it.name }
    }

    private fun searchTeams(
        query: String?,
        limit: Int,
    ): String {
        val matched = teamService.search(TeamSearchFilter()).matching(query) { it.name }
        val teams =
            matched.take(limit).map {
                mapOf("id" to it.id, "name" to it.name, "leader" to it.leaderName, "members" to it.members.map { m -> m.name })
            }
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
                .onEach { idRegistry.register(ChatV2IdRegistry.TASK, it.id) }.map(support::taskMap)
        val epics =
            sortEpics(epicMatches, sort, order).take(limit)
                .onEach { idRegistry.register(ChatV2IdRegistry.EPIC, it.id) }.map(support::epicMap)
        val projects =
            sortProjects(projectMatches, sort, order).take(limit)
                .onEach { idRegistry.register(ChatV2IdRegistry.PROJECT, it.id) }.map(support::projectMap)

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
        val dueFrom: LocalDate?,
        val dueTo: LocalDate?,
        val excludeDone: Boolean,
        val scopeStart: LocalDate,
    ) {
        companion object {
            fun from(
                args: SearchItemsArgs,
                query: String?,
                currentUserId: Long,
            ) = Criteria(
                query = query,
                statusRaw = args.status,
                assigneeId = args.assigneeId ?: if (args.assigneeMe == true) currentUserId else null,
                projectId = args.projectId,
                epicId = args.epicId,
                dueFrom = args.dueDateFrom?.let(LocalDate::parse),
                dueTo = args.dueDateTo?.let(LocalDate::parse),
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
    status != null || assigneeMe == true || assigneeId != null || projectId != null ||
        epicId != null || dueDateFrom != null || dueDateTo != null || excludeDone == true

/** query 이름 토큰 매칭 필터 — query 없으면 전체 통과 */
private inline fun <T> List<T>.matching(
    query: String?,
    name: (T) -> String,
): List<T> = filter { query == null || ItemNameMatcher.matches(query, name(it)) }
