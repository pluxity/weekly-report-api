package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.util.ChatScope
import com.pluxity.weekly.chat.v2.dto.AggregateItemsArgs
import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.service.TaskService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * aggregate_items 실행부 — 그룹별 개수·평균 진행률 집계.
 * 검색은 타입당 limit건 캡이 있어 "몇 개/얼마나" 질문에 못 쓰므로, 서비스 search의 전체 결과
 * (권한 스코프 적용됨)를 받아 서버에서 groupBy 한다.
 * 부모·담당자 필터는 이름으로 받아 서버가 id로 해소한다([ChatV2ToolSupport.resolveByName]) — search_items와 동일.
 */
@Component
class AggregateItemsHandler(
    private val taskService: TaskService,
    private val epicService: EpicService,
    private val projectService: ProjectService,
    private val userRepository: UserRepository,
    private val support: ChatV2ToolSupport,
    private val objectMapper: ObjectMapper,
) {
    fun handle(
        argumentsJson: String,
        currentUserId: Long,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = support.readArgs<AggregateItemsArgs>(argumentsJson)

        val groupBy = args.groupBy.lowercase()
        val scopeStart = ChatScope.scopeStartDate()
        // 부모·담당자 필터는 이름 → 서버가 id로 해소 (모델이 id를 지어낼 여지 제거)
        val resolvedAssigneeId =
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
                        else -> return support.errorResult("task 집계의 group_by는 status/project/epic/assignee만 가능합니다.")
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
                        else -> return support.errorResult("epic 집계의 group_by는 status/project만 가능합니다.")
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
                if (groupBy != "status") return support.errorResult("project 집계의 group_by는 status만 가능합니다.")
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
            else -> support.errorResult("집계할 수 없는 종류: ${args.type} (task/epic/project만 가능)")
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
}
