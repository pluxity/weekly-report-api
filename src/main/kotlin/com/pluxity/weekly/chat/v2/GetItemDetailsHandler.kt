package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.chat.v2.dto.GetItemDetailsArgs
import com.pluxity.weekly.epic.service.EpicService
import com.pluxity.weekly.project.service.ProjectService
import com.pluxity.weekly.task.service.TaskService
import com.pluxity.weekly.team.service.TeamService
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * get_item_details 실행부 — 태스크·업무 그룹·프로젝트·팀 1건의 상세 조회.
 * 이번 턴 검색으로 등록된 id만 허용해([ChatV2IdRegistry]) 모델이 지어낸 id 조회를 차단하고,
 * 검색 결과에는 없던 필드(설명·시작일·구성원·진행률)까지 담아 반환한다.
 * 하위 항목(프로젝트의 에픽·태스크 등)은 주지 않는다 — 확장은 search_items(project_id 등)로(점진 공개).
 */
@Component
class GetItemDetailsHandler(
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
        val args = support.readArgs<GetItemDetailsArgs>(argumentsJson)
        val type = ChatV2EntityType.from(args.type)
        if (type == null || type !in DETAILABLE) {
            return support.errorResult("상세 조회할 수 없는 종류: ${args.type} (task/epic/project/team만 가능)")
        }
        support.validateKnown(idRegistry, type, args.id, "id")?.let { return it }
        val detail: Map<String, Any?> =
            when (type) {
                ChatV2EntityType.TASK -> {
                    val t = taskService.findById(args.id)
                    support.taskMap(t) +
                        mapOf(
                            "description" to t.description,
                            "start_date" to t.startDate?.toString(),
                        )
                }
                ChatV2EntityType.EPIC -> {
                    val e = epicService.findById(args.id)
                    support.epicMap(e) +
                        mapOf(
                            "description" to e.description,
                            "start_date" to e.startDate?.toString(),
                        )
                }
                ChatV2EntityType.PROJECT -> {
                    val p = projectService.findById(args.id)
                    support.projectMap(p) +
                        mapOf(
                            "description" to p.description,
                            "start_date" to p.startDate?.toString(),
                            "progress" to p.progress,
                            "members" to p.members.map { it.userName },
                        )
                }
                else -> support.teamMap(teamService.findById(args.id)) // TEAM
            }
        return objectMapper.writeValueAsString(mapOf(type.key to detail))
    }

    companion object {
        /** 상세 조회 가능한 종류 (USER는 search_users 전용이라 제외) */
        private val DETAILABLE =
            setOf(
                ChatV2EntityType.TASK,
                ChatV2EntityType.EPIC,
                ChatV2EntityType.PROJECT,
                ChatV2EntityType.TEAM,
            )
    }
}
