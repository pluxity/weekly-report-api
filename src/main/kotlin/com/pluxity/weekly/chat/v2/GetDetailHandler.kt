package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.chat.dto.TaskSearchFilter
import com.pluxity.weekly.chat.dto.TeamSearchFilter
import com.pluxity.weekly.chat.util.ChatScope
import com.pluxity.weekly.chat.v2.dto.GetDetailArgs
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.service.TeamService
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * get_detail 실행부 — 한 항목(태스크·업무 그룹·프로젝트·팀)의 **상세**.
 *
 * "X 자세히/설명/구성" 류를 search_items 좁힘(자동 드릴인)에 의존하지 않고 전용 도구로 확실히 처리한다.
 * id 추측은 이 세션의 금기라, 모델은 **이름만** 넘기고 서버가 이름→단건으로 해소한다([ItemNameMatcher]).
 * 0건은 not-found, 다건은 되묻기 안내로 되돌리고, 단건일 때만 무거운 필드(설명·구성원·시작일)와 하위 트리를 붙인다.
 * 상세 필드는 search 결과 DTO가 이미 다 들고 있어 재조회가 없다(자식 트리만 추가 조회).
 */
@Component
class GetDetailHandler(
    private val taskService: TaskService,
    private val epicService: EpicService,
    private val projectService: ProjectService,
    private val teamService: TeamService,
    private val support: ChatV2ToolSupport,
    private val objectMapper: ObjectMapper,
) {
    fun handle(
        argumentsJson: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val args = support.readArgs<GetDetailArgs>(argumentsJson)
        val name = args.name.trim().takeIf { it.isNotBlank() }
            ?: return support.errorResult("항목 이름(name)을 지정하세요.")

        return when (ChatV2EntityType.from(args.type)) {
            ChatV2EntityType.TASK -> detailTask(name, idRegistry)
            ChatV2EntityType.EPIC -> detailEpic(name, idRegistry)
            ChatV2EntityType.PROJECT -> detailProject(name, idRegistry)
            ChatV2EntityType.TEAM -> detailTeam(name, idRegistry)
            else -> support.errorResult("알 수 없는 종류: ${args.type} (task/epic/project/team만 가능)")
        }
    }

    private fun detailTask(
        name: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val scope = ChatScope.scopeStartDate()
        val matched = taskService.search(TaskSearchFilter(scopeStartDate = scope))
            .filter { ItemNameMatcher.matches(name, it.name) }
        return resolveSingle(matched, name, "태스크", { it.name }) { task ->
            idRegistry.register(ChatV2EntityType.TASK, task.id)
            objectMapper.writeValueAsString(mapOf(ChatV2EntityType.TASK.key to support.taskDetailMap(task)))
        }
    }

    private fun detailEpic(
        name: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val scope = ChatScope.scopeStartDate()
        val matched = epicService.search(EpicSearchFilter(scopeStartDate = scope))
            .filter { ItemNameMatcher.matches(name, it.name) }
        return resolveSingle(matched, name, "업무 그룹", { it.name }) { epic ->
            idRegistry.register(ChatV2EntityType.EPIC, epic.id)
            objectMapper.writeValueAsString(
                mapOf(ChatV2EntityType.EPIC.key to support.epicDetailMap(epic) + epicChildren(epic.id)),
            )
        }
    }

    private fun detailProject(
        name: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val scope = ChatScope.scopeStartDate()
        val matched = projectService.search(ProjectSearchFilter(scopeStartDate = scope))
            .filter { ItemNameMatcher.matches(name, it.name) }
        return resolveSingle(matched, name, "프로젝트", { it.name }) { project ->
            idRegistry.register(ChatV2EntityType.PROJECT, project.id)
            objectMapper.writeValueAsString(
                mapOf(
                    ChatV2EntityType.PROJECT.key to
                        support.projectDetailMap(project) + mapOf("epics" to projectChildEpics(project.id)),
                ),
            )
        }
    }

    private fun detailTeam(
        name: String,
        idRegistry: ChatV2IdRegistry,
    ): String {
        val matched = teamService.search(TeamSearchFilter())
            .filter { ItemNameMatcher.matches(name, it.name) }
        return resolveSingle(matched, name, "팀", { it.name }) { team ->
            idRegistry.register(ChatV2EntityType.TEAM, team.id)
            // findMembers로 구성원을 다시 채운다(findById가 멤버를 비우는 이슈 회피 — search가 채운 members와 동일 경로).
            objectMapper.writeValueAsString(
                mapOf(
                    ChatV2EntityType.TEAM.key to
                        support.teamMap(team) + mapOf("members" to teamService.findMembers(team.id).map { it.name }),
                ),
            )
        }
    }

    /** 0건 → not-found, 다건 → 되묻기, 단건 → [detail] 상세 조립. */
    private fun <T> resolveSingle(
        matched: List<T>,
        name: String,
        label: String,
        nameOf: (T) -> String,
        detail: (T) -> String,
    ): String =
        when (matched.size) {
            0 -> support.errorResult("'$name' $label 을(를) 찾을 수 없습니다.")
            1 -> detail(matched.first())
            else ->
                support.errorResult(
                    "'$name'에 해당하는 $label 이(가) 여러 개입니다: " +
                        matched.take(5).joinToString(", ", transform = nameOf) + ". 어느 것인지 알려주세요.",
                )
        }

    // ── 자식 트리 (상세 조회 시 부착) ──

    /** 에픽의 하위 태스크 (캡 + 전체 개수). */
    private fun epicChildren(epicId: Long): Map<String, Any?> {
        val tasks = taskService.search(TaskSearchFilter(epicId = epicId, scopeStartDate = ChatScope.scopeStartDate()))
        return mapOf(
            "task_count" to tasks.size,
            "tasks" to tasks.take(TASK_CHILD_CAP).map { mapOf("name" to it.name, "status" to it.status.name) },
        )
    }

    /** 프로젝트의 하위 에픽(각 하위 태스크 캡). 태스크는 프로젝트 단위로 한 번 조회해 epicId로 groupBy (N+1 회피). */
    private fun projectChildEpics(projectId: Long): List<Map<String, Any?>> {
        val scope = ChatScope.scopeStartDate()
        val epics = epicService.search(EpicSearchFilter(projectId = projectId, scopeStartDate = scope))
        val tasksByEpic =
            taskService.search(TaskSearchFilter(projectId = projectId, scopeStartDate = scope)).groupBy { it.epicId }
        return epics.take(EPIC_CHILD_CAP).map { e ->
            val eTasks = tasksByEpic[e.id].orEmpty()
            mapOf(
                "name" to e.name,
                "status" to e.status.name,
                "task_count" to eTasks.size,
                "tasks" to eTasks.take(TASK_CHILD_CAP).map { mapOf("name" to it.name, "status" to it.status.name) },
            )
        }
    }

    companion object {
        // 하위 트리 캡 (토큰 방어)
        private const val EPIC_CHILD_CAP = 10
        private const val TASK_CHILD_CAP = 10
    }
}
