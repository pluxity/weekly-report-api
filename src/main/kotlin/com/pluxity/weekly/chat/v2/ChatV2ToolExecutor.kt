package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.util.ChatScope
import com.pluxity.weekly.chat.v2.dto.AggregateItemsArgs
import com.pluxity.weekly.chat.v2.dto.GetTaskHistoryArgs
import com.pluxity.weekly.chat.v2.dto.GetWeeklyReportArgs
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
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
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
 *
 * god-class 분해 중 — 도구별 로직은 핸들러(예: [SearchItemsHandler])로, 공통 부품은 [ChatV2ToolSupport]로 이동.
 * execute()는 도구명 → 핸들러/메서드 dispatch만 담당한다.
 */
@Component
class ChatV2ToolExecutor(
    private val taskService: TaskService,
    private val taskReviewService: TaskReviewService,
    private val epicService: EpicService,
    private val projectService: ProjectService,
    private val teamRepository: TeamRepository,
    private val weeklyReportService: WeeklyReportService,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val support: ChatV2ToolSupport,
    private val searchItemsHandler: SearchItemsHandler,
    private val getItemDetailsHandler: GetItemDetailsHandler,
) {
    fun execute(
        toolName: String,
        argumentsJson: String,
        currentUserId: Long,
        idRegistry: ChatV2IdRegistry,
    ): String =
        try {
            when (toolName) {
                ChatV2Tools.SEARCH_ITEMS -> searchItemsHandler.handle(argumentsJson, currentUserId, idRegistry)
                ChatV2Tools.SEARCH_USERS -> searchUsers(argumentsJson, idRegistry)
                ChatV2Tools.GET_ITEM_DETAILS -> getItemDetailsHandler.handle(argumentsJson, idRegistry)
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
                .onEach { idRegistry.register(ChatV2EntityType.USER, it.requiredId) }
                .map {
                    mapOf(
                        "id" to it.requiredId,
                        "name" to it.name,
                        "roles" to it.getRoles().map { r -> r.name },
                    )
                }
        return objectMapper.writeValueAsString(mapOf("users" to users))
    }

    // ── 집계 / 이력 ──

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

        val groupBy = args.groupBy.lowercase()
        val scopeStart = ChatScope.scopeStartDate()
        // 부모·담당자 필터는 이름 → 서버가 id로 해소 (search_items와 동일 — 모델이 id를 지어낼 여지 제거)
        val resolvedAssigneeId =
            when (val r = support.resolveByName(args.assignee, "사용자", ChatV2EntityType.USER, idRegistry) {
                userRepository.findAllBy(Sort.by("name")).map { it.requiredId to it.name }
            }) {
                is NameResolution.Error -> return errorResult(r.message)
                is NameResolution.Resolved -> r.id
                NameResolution.NotRequested -> null
            }
        val projectId =
            when (val r = support.resolveByName(args.project, "프로젝트", ChatV2EntityType.PROJECT, idRegistry) {
                projectService.search(ProjectSearchFilter(scopeStartDate = scopeStart)).map { it.id to it.name }
            }) {
                is NameResolution.Error -> return errorResult(r.message)
                is NameResolution.Resolved -> r.id
                NameResolution.NotRequested -> null
            }
        val epicId =
            when (val r = support.resolveByName(args.epic, "업무 그룹", ChatV2EntityType.EPIC, idRegistry) {
                epicService.search(EpicSearchFilter(scopeStartDate = scopeStart)).map { it.id to it.name }
            }) {
                is NameResolution.Error -> return errorResult(r.message)
                is NameResolution.Resolved -> r.id
                NameResolution.NotRequested -> null
            }
        val dueFrom = args.dueDateFrom?.let(LocalDate::parse)
        val dueTo = args.dueDateTo?.let(LocalDate::parse)
        val assigneeId = resolvedAssigneeId ?: if (args.assigneeMe == true) currentUserId else null
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
                            epicId = epicId,
                            projectId = projectId,
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
                            projectId = projectId,
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
                .onEach { idRegistry.register(ChatV2EntityType.TASK, it.taskId) }
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
        validateKnown(idRegistry, ChatV2EntityType.TASK, args.taskId, "task_id")?.let { return it }
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

    // ── 공용 부품 위임 (god-class 분해 과도기 — 각 메서드가 핸들러로 이동하면 함께 사라진다) ──

    private inline fun <reified T> readArgs(json: String): T = support.readArgs(json)

    private fun validateKnown(
        idRegistry: ChatV2IdRegistry,
        type: ChatV2EntityType,
        id: Long?,
        field: String,
    ): String? = support.validateKnown(idRegistry, type, id, field)

    private fun errorResult(message: String): String = support.errorResult(message)

    companion object {
        private const val MAX_USER_RESULTS = 20
    }
}
