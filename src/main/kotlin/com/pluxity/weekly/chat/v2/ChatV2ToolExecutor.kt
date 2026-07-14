package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.dto.TeamSearchFilter
import com.pluxity.weekly.chat.util.ChatScope
import com.pluxity.weekly.chat.v2.dto.AggregateItemsArgs
import com.pluxity.weekly.chat.v2.dto.GetItemDetailsArgs
import com.pluxity.weekly.chat.v2.dto.GetTaskHistoryArgs
import com.pluxity.weekly.chat.v2.dto.GetWeeklyReportArgs
import com.pluxity.weekly.chat.v2.dto.SearchItemsArgs
import com.pluxity.weekly.chat.v2.dto.SearchUsersArgs
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.report.dto.ReportItem
import com.pluxity.weekly.report.service.WeeklyReportService
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.service.TaskReviewService
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.repository.TeamRepository
import com.pluxity.weekly.team.service.TeamService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.exc.UnrecognizedPropertyException
import java.time.LocalDate
import kotlin.math.roundToInt

private val log = KotlinLogging.logger {}

/**
 * tool_calls 실행부 — **조회 전용**. 모델은 조회를 "요청"만 하고, 실제 조회 범위·권한은
 * 기존 서비스가 담당한다 (각 Service.search의 AuthorizationService 조회 스코프,
 * findApprovalLogs의 requireEpicAccess 등). CUD는 보드/폼에서 처리하므로 여기엔 없다.
 *
 * 실행 실패는 예외로 터뜨리지 않고 {"error": "..."} 결과로 모델에게 되돌려,
 * 모델이 사용자에게 자연어로 안내하거나 다른 시도를 하게 한다 (agent 패턴).
 */
@Component
class ChatV2ToolExecutor(
    private val taskService: TaskService,
    private val taskReviewService: TaskReviewService,
    private val epicService: EpicService,
    private val projectService: ProjectService,
    private val teamService: TeamService,
    private val teamRepository: TeamRepository,
    private val weeklyReportService: WeeklyReportService,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    fun execute(
        toolName: String,
        argumentsJson: String,
        currentUserId: Long,
        idRegistry: ChatV2IdRegistry,
    ): String =
        try {
            when (toolName) {
                ChatV2Tools.SEARCH_ITEMS -> searchItems(argumentsJson, currentUserId, idRegistry)
                ChatV2Tools.SEARCH_USERS -> searchUsers(argumentsJson, idRegistry)
                ChatV2Tools.GET_ITEM_DETAILS -> getItemDetails(argumentsJson, idRegistry)
                ChatV2Tools.AGGREGATE_ITEMS -> aggregateItems(argumentsJson, currentUserId, idRegistry)
                ChatV2Tools.LIST_PENDING_REVIEWS -> listPendingReviews(idRegistry)
                ChatV2Tools.GET_TASK_HISTORY -> getTaskHistory(argumentsJson, idRegistry)
                ChatV2Tools.GET_WEEKLY_REPORT -> getWeeklyReport(argumentsJson, currentUserId)
                else -> errorResult("알 수 없는 도구: $toolName")
            }
        } catch (e: UnrecognizedPropertyException) {
            // 모델이 지어낸 인자를 조용히 버리면 "필터 없는 결과"가 환각으로 이어진다 — 거부하고 스키마를 알려 재시도 유도
            log.info { "chat/v2 tool 인자 거부: $toolName — ${e.propertyName} (args=$argumentsJson)" }
            errorResult("지원하지 않는 인자: ${e.propertyName}. 사용 가능한 인자: ${e.knownPropertyIds.joinToString(", ")}")
        } catch (e: CustomException) {
            log.info { "chat/v2 tool 실행 거부: $toolName — ${e.message}" }
            errorResult(e.message ?: "요청을 처리할 수 없습니다.")
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 tool 실행 실패: $toolName args=$argumentsJson" }
            errorResult("도구 실행 중 오류가 발생했습니다: ${e.message}")
        }

    // ── 검색 ──

    /**
     * 태스크·업무 그룹·프로젝트·팀 통합 검색.
     * 사용자가 타입을 안 붙이고 이름만 말하는 실사용 패턴에 맞춰 3계층을 한 번에 뒤진다 (팀은 명시 시에만).
     * 이름 매칭은 in-memory 토큰 매칭([ItemNameMatcher]) — "cctv API" ↔ "CCTV 목록 API".
     * 결과는 타입당 limit건으로 캡하되 totals에 전체 개수를 담아 "잘렸는지"를 모델이 알게 한다.
     */
    private fun searchItems(
        argumentsJson: String,
        currentUserId: Long,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = readArgs<SearchItemsArgs>(argumentsJson)
        val query = args.query?.trim()?.takeIf { it.isNotBlank() }
        val type = args.type?.lowercase()
        val hasFilter =
            args.status != null || args.assigneeMe == true || args.assigneeId != null ||
                args.projectId != null || args.epicId != null || args.dueDateFrom != null ||
                args.dueDateTo != null || args.excludeDone == true
        if (type == null && query == null && !hasFilter) {
            return errorResult("query 또는 필터(type/status/assignee_me/assignee_id/기간)를 하나 이상 지정하세요.")
        }
        // 검색 필터의 id도 검색으로 확인된 값만 — 지어낸 id로 빈 결과가 나가면 "없다"는 거짓 부정으로 이어진다
        // (실사례: 알파 프로젝트=10인데 project_id=3으로 검색 → "알파에는 없습니다")
        validateKnown(idRegistry, ChatV2IdRegistry.USER, args.assigneeId, "assignee_id")?.let { return it }
        validateKnown(idRegistry, ChatV2IdRegistry.PROJECT, args.projectId, "project_id")?.let { return it }
        validateKnown(idRegistry, ChatV2IdRegistry.EPIC, args.epicId, "epic_id")?.let { return it }

        val limit = (args.limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

        if (type == "team") {
            val matched =
                teamService
                    .search(TeamSearchFilter())
                    .filter { query == null || ItemNameMatcher.matches(query, it.name) }
            val teams =
                matched.take(limit).map {
                    mapOf(
                        "id" to it.id,
                        "name" to it.name,
                        "leader" to it.leaderName,
                        "members" to it.members.map { m -> m.name },
                    )
                }
            return objectMapper.writeValueAsString(
                mapOf("teams" to teams, "totals" to mapOf("teams" to matched.size), "truncated" to (matched.size > limit)),
            )
        }

        val scopeStart = ChatScope.scopeStartDate()
        val dueFrom = args.dueDateFrom?.let(LocalDate::parse)
        val dueTo = args.dueDateTo?.let(LocalDate::parse)
        val assigneeId = args.assigneeId ?: if (args.assigneeMe == true) currentUserId else null
        val excludeDone = args.excludeDone ?: false

        // 소속/담당 필터가 있으면 의미 없는 계층은 건너뛴다 (epic_id → 태스크만, 담당자 필터 → 프로젝트 제외)
        val includeTasks = type == null || type == "task"
        val includeEpics = (type == null || type == "epic") && args.epicId == null
        val includeProjects = (type == null || type == "project") && args.epicId == null && assigneeId == null

        val taskMatches =
            if (includeTasks) {
                val status = args.status?.let { s -> TaskStatus.entries.find { it.name.equals(s, ignoreCase = true) } }
                if (args.status != null && status == null) {
                    emptyList()
                } else {
                    taskService
                        .search(
                            TaskSearchFilter(
                                status = status,
                                epicId = args.epicId,
                                projectId = args.projectId,
                                assigneeId = assigneeId,
                                dueDateFrom = dueFrom,
                                dueDateTo = dueTo,
                                excludeDone = excludeDone,
                                scopeStartDate = scopeStart,
                            ),
                        ).filter { query == null || ItemNameMatcher.matches(query, it.name) }
                }
            } else {
                emptyList()
            }

        val epicMatches =
            if (includeEpics) {
                val status = args.status?.let { s -> EpicStatus.entries.find { it.name.equals(s, ignoreCase = true) } }
                if (args.status != null && status == null) {
                    emptyList()
                } else {
                    epicService
                        .search(
                            EpicSearchFilter(
                                status = status,
                                projectId = args.projectId,
                                assigneeId = assigneeId,
                                dueDateFrom = dueFrom,
                                dueDateTo = dueTo,
                                excludeDone = excludeDone,
                                scopeStartDate = scopeStart,
                            ),
                        ).filter { query == null || ItemNameMatcher.matches(query, it.name) }
                }
            } else {
                emptyList()
            }

        val projectMatches =
            if (includeProjects) {
                val status = args.status?.let { s -> ProjectStatus.entries.find { it.name.equals(s, ignoreCase = true) } }
                if (args.status != null && status == null) {
                    emptyList()
                } else {
                    projectService
                        .search(
                            ProjectSearchFilter(
                                status = status,
                                dueDateFrom = dueFrom,
                                dueDateTo = dueTo,
                                excludeDone = excludeDone,
                                scopeStartDate = scopeStart,
                            ),
                        ).filter { query == null || ItemNameMatcher.matches(query, it.name) }
                }
            } else {
                emptyList()
            }

        val tasks =
            sortTasks(taskMatches, args.sort, args.order)
                .take(limit)
                .onEach { idRegistry.register(ChatV2IdRegistry.TASK, it.id) }
                .map(::taskMap)
        val epics =
            sortEpics(epicMatches, args.sort, args.order)
                .take(limit)
                .onEach { idRegistry.register(ChatV2IdRegistry.EPIC, it.id) }
                .map(::epicMap)
        val projects =
            sortProjects(projectMatches, args.sort, args.order)
                .take(limit)
                .onEach { idRegistry.register(ChatV2IdRegistry.PROJECT, it.id) }
                .map(::projectMap)

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

    private fun searchUsers(
        argumentsJson: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = readArgs<SearchUsersArgs>(argumentsJson)
        val query = args.query?.trim()?.takeIf { it.isNotBlank() }
        val role = args.role?.trim()?.takeIf { it.isNotBlank() }
        val users =
            userRepository
                .findAllBy(Sort.by("name"))
                .filter { query == null || ItemNameMatcher.matches(query, it.name) }
                .filter { user -> role == null || user.userRoles.any { it.role.name.equals(role, ignoreCase = true) } }
                .distinctBy { it.requiredId }
                .take(MAX_USER_RESULTS)
                .onEach { idRegistry.register(ChatV2IdRegistry.USER, it.requiredId) }
                .map {
                    mapOf(
                        "id" to it.requiredId,
                        "name" to it.name,
                        "roles" to it.getRoles().map { r -> r.name },
                    )
                }
        return objectMapper.writeValueAsString(mapOf("users" to users))
    }

    // ── 상세 / 집계 / 이력 ──

    private fun getItemDetails(
        argumentsJson: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = readArgs<GetItemDetailsArgs>(argumentsJson)
        val type = args.type.lowercase()
        if (type !in setOf(ChatV2IdRegistry.TASK, ChatV2IdRegistry.EPIC, ChatV2IdRegistry.PROJECT)) {
            return errorResult("상세 조회할 수 없는 종류: ${args.type} (task/epic/project만 가능)")
        }
        validateKnown(idRegistry, type, args.id, "id")?.let { return it }
        val detail: Map<String, Any?> =
            when (type) {
                ChatV2IdRegistry.TASK -> {
                    val t = taskService.findById(args.id)
                    taskMap(t) +
                        mapOf(
                            "description" to t.description,
                            "start_date" to t.startDate?.toString(),
                        )
                }
                ChatV2IdRegistry.EPIC -> {
                    val e = epicService.findById(args.id)
                    epicMap(e) +
                        mapOf(
                            "description" to e.description,
                            "start_date" to e.startDate?.toString(),
                        )
                }
                else -> {
                    val p = projectService.findById(args.id)
                    projectMap(p) +
                        mapOf(
                            "description" to p.description,
                            "start_date" to p.startDate?.toString(),
                            "progress" to p.progress,
                            "members" to p.members.map { it.userName },
                        )
                }
            }
        return objectMapper.writeValueAsString(mapOf(type to detail))
    }

    /**
     * 그룹별 개수·평균 진행률 집계. 검색은 타입당 limit건 캡이 있어 "몇 개/얼마나" 질문에 못 쓰므로,
     * 서비스 search의 전체 결과(권한 스코프 적용됨)를 받아 서버에서 groupBy 한다.
     */
    private fun aggregateItems(
        argumentsJson: String,
        currentUserId: Long,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = readArgs<AggregateItemsArgs>(argumentsJson)
        validateKnown(idRegistry, ChatV2IdRegistry.USER, args.assigneeId, "assignee_id")?.let { return it }
        validateKnown(idRegistry, ChatV2IdRegistry.PROJECT, args.projectId, "project_id")?.let { return it }
        validateKnown(idRegistry, ChatV2IdRegistry.EPIC, args.epicId, "epic_id")?.let { return it }

        val groupBy = args.groupBy.lowercase()
        val scopeStart = ChatScope.scopeStartDate()
        val dueFrom = args.dueDateFrom?.let(LocalDate::parse)
        val dueTo = args.dueDateTo?.let(LocalDate::parse)
        val assigneeId = args.assigneeId ?: if (args.assigneeMe == true) currentUserId else null
        val excludeDone = args.excludeDone ?: false

        return when (args.type.lowercase()) {
            "task" -> {
                val keyOf: (TaskResponse) -> String =
                    when (groupBy) {
                        "status" -> { t -> t.status.name }
                        "project" -> { t -> t.projectName }
                        "epic" -> { t -> t.epicName }
                        "assignee" -> { t -> t.assigneeName ?: "(미지정)" }
                        else -> return errorResult("task 집계의 group_by는 status/project/epic/assignee만 가능합니다.")
                    }
                val status = args.status?.let { s -> TaskStatus.entries.find { it.name.equals(s, ignoreCase = true) } }
                val tasks =
                    taskService.search(
                        TaskSearchFilter(
                            status = status,
                            epicId = args.epicId,
                            projectId = args.projectId,
                            assigneeId = assigneeId,
                            dueDateFrom = dueFrom,
                            dueDateTo = dueTo,
                            excludeDone = excludeDone,
                            scopeStartDate = scopeStart,
                        ),
                    )
                aggregateResult("task", groupBy, tasks.size, tasks.groupBy(keyOf)) { group ->
                    group.map { it.progress }.average().roundToInt()
                }
            }
            "epic" -> {
                val keyOf: (EpicResponse) -> String =
                    when (groupBy) {
                        "status" -> { e -> e.status.name }
                        "project" -> { e -> e.projectName }
                        else -> return errorResult("epic 집계의 group_by는 status/project만 가능합니다.")
                    }
                val status = args.status?.let { s -> EpicStatus.entries.find { it.name.equals(s, ignoreCase = true) } }
                val epics =
                    epicService.search(
                        EpicSearchFilter(
                            status = status,
                            projectId = args.projectId,
                            assigneeId = assigneeId,
                            dueDateFrom = dueFrom,
                            dueDateTo = dueTo,
                            excludeDone = excludeDone,
                            scopeStartDate = scopeStart,
                        ),
                    )
                aggregateResult("epic", groupBy, epics.size, epics.groupBy(keyOf), avgProgress = null)
            }
            "project" -> {
                if (groupBy != "status") return errorResult("project 집계의 group_by는 status만 가능합니다.")
                val status = args.status?.let { s -> ProjectStatus.entries.find { it.name.equals(s, ignoreCase = true) } }
                val projects =
                    projectService.search(
                        ProjectSearchFilter(
                            status = status,
                            dueDateFrom = dueFrom,
                            dueDateTo = dueTo,
                            excludeDone = excludeDone,
                            scopeStartDate = scopeStart,
                        ),
                    )
                aggregateResult("project", groupBy, projects.size, projects.groupBy { it.status.name }) { group ->
                    group.map { it.progress }.average().roundToInt()
                }
            }
            else -> errorResult("집계할 수 없는 종류: ${args.type} (task/epic/project만 가능)")
        }
    }

    private fun <T> aggregateResult(
        type: String,
        groupBy: String,
        total: Int,
        grouped: Map<String, List<T>>,
        avgProgress: ((List<T>) -> Int)? = null,
    ): String {
        val groups =
            grouped.entries
                .sortedByDescending { it.value.size }
                .map { (key, items) ->
                    buildMap<String, Any?> {
                        put("group", key)
                        put("count", items.size)
                        if (avgProgress != null) put("avg_progress", avgProgress(items))
                    }
                }
        return objectMapper.writeValueAsString(
            mapOf("type" to type, "group_by" to groupBy, "total" to total, "groups" to groups),
        )
    }

    private fun listPendingReviews(idRegistry: ChatV2IdRegistry): String {
        val pending =
            taskReviewService
                .findPendingReviews()
                .take(MAX_USER_RESULTS)
                .onEach { idRegistry.register(ChatV2IdRegistry.TASK, it.taskId) }
                .map {
                    mapOf(
                        "id" to it.taskId,
                        "name" to it.taskName,
                        "project" to it.projectName,
                        "epic" to it.epicName,
                        "assignee" to it.assigneeName,
                    )
                }
        return objectMapper.writeValueAsString(mapOf("pending_reviews" to pending))
    }

    private fun getTaskHistory(
        argumentsJson: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = readArgs<GetTaskHistoryArgs>(argumentsJson)
        validateKnown(idRegistry, ChatV2IdRegistry.TASK, args.taskId, "task_id")?.let { return it }
        // 권한은 findApprovalLogs 내장 requireEpicAccess가 담당
        val history =
            taskReviewService.findApprovalLogs(args.taskId).map {
                mapOf(
                    "action" to it.action.name,
                    "actor" to it.actorName,
                    "reason" to it.reason,
                    "at" to it.createdAt.toString(),
                )
            }
        return objectMapper.writeValueAsString(mapOf("task_id" to args.taskId, "history" to history))
    }

    /**
     * 내 팀 주간보고 조회 — v1 handleRead 규칙 재사용 (팀 리더 게이트, week 해석은 findForChat의 resolveWeekStart).
     * rawContent(원문 전체)는 제외하고 정리된 항목·지난주 매칭만 모델에 준다.
     */
    private fun getWeeklyReport(
        argumentsJson: String,
        currentUserId: Long,
    ): String {
        val args = readArgs<GetWeeklyReportArgs>(argumentsJson)
        teamRepository.findByLeaderId(currentUserId).firstOrNull()
            ?: return errorResult("주간보고는 팀 리더만 조회할 수 있습니다.")
        val report =
            weeklyReportService.findForChat(args.week)
                ?: return objectMapper.writeValueAsString(
                    mapOf("weekly_report" to null, "message" to "해당 주차에 작성된 주간보고가 없습니다."),
                )
        val matched =
            report.matchedAgainstPrev?.let { m ->
                mapOf(
                    "matched" to m.matched.map { mapOf("assignee" to it.assignee, "prev" to it.prev, "curr" to it.curr) },
                    "missing_from_prev_plan" to m.missing.map { mapOf("assignee" to it.assignee, "text" to it.text) },
                    "new_this_week" to m.new.map { mapOf("assignee" to it.assignee, "text" to it.text) },
                )
            }
        return objectMapper.writeValueAsString(
            mapOf(
                "weekly_report" to
                    mapOf(
                        "team" to report.teamName,
                        "week_start" to report.weekStart.toString(),
                        "this_week" to report.formatted.thisWeek.map(::reportItemMap),
                        "next_week" to report.formatted.nextWeek.map(::reportItemMap),
                        "issues" to report.formatted.issues.map(::reportItemMap),
                        "others" to report.formatted.others.map(::reportItemMap),
                        "matched_against_prev" to matched,
                    ),
            ),
        )
    }

    private fun reportItemMap(item: ReportItem): Map<String, Any?> =
        mapOf(
            "assignee" to item.assignee,
            "category" to item.category,
            "text" to item.text,
            "progress" to item.progress,
            "due_date" to item.dueDate?.toString(),
        )

    // ── 공통 ──

    /** 스키마에 없는 인자는 [UnrecognizedPropertyException]으로 실패시킨다 — execute()가 error 결과로 변환. */
    private inline fun <reified T> readArgs(json: String): T =
        objectMapper
            .readerFor(T::class.java)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .readValue(json)

    /**
     * 모델이 지어낸 id 차단 — 이번 턴 검색 결과에 없던 id는 실행 전에 거부한다.
     * (프롬프트 규칙만으로는 검색을 건너뛰고 id를 찍는 사례가 실제로 반복 발생)
     */
    private fun validateKnown(
        idRegistry: ChatV2IdRegistry,
        type: String,
        id: Long?,
        field: String,
    ): String? {
        if (id == null || idRegistry.isKnown(type, id)) return null
        return errorResult(
            "$field=$id 는 이번 턴에서 검색으로 확인되지 않은 id입니다. " +
                "search_items/search_users로 먼저 검색해 결과의 id만 사용하세요.",
        )
    }

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

    private fun taskMap(task: TaskResponse): Map<String, Any?> =
        mapOf(
            "id" to task.id,
            "name" to task.name,
            "project" to task.projectName,
            "epic" to task.epicName,
            "status" to task.status.name,
            "progress" to task.progress,
            "due_date" to task.dueDate?.toString(),
            "assignee" to task.assigneeName,
        )

    private fun epicMap(epic: EpicResponse): Map<String, Any?> =
        mapOf(
            "id" to epic.id,
            "name" to epic.name,
            "project" to epic.projectName,
            "status" to epic.status.name,
            "due_date" to epic.dueDate?.toString(),
            "members" to epic.members.map { it.userName },
        )

    private fun projectMap(project: ProjectResponse): Map<String, Any?> =
        mapOf(
            "id" to project.id,
            "name" to project.name,
            "status" to project.status.name,
            "due_date" to project.dueDate?.toString(),
            "pm" to project.pmName,
        )

    private fun errorResult(message: String): String = objectMapper.writeValueAsString(mapOf("error" to message))

    companion object {
        private const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 30
        private const val MAX_USER_RESULTS = 20
    }
}
