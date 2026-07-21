package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.task.dto.TaskResponse
import com.pluxity.weekly.team.dto.TeamResponse
import org.springframework.stereotype.Component
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper

/** 부모 필터 이름 해소 결과. */
sealed interface NameResolution {
    /** 이름 필터를 주지 않음 — 필터 없음. */
    data object NotRequested : NameResolution

    /** 유일 매칭 — 이 id로 필터. */
    data class Resolved(val id: Long) : NameResolution

    /** 0건 또는 다건 — 모델에게 안내(되묻기). */
    data class Error(val message: String) : NameResolution
}

/**
 * chat/v2 도구 실행 공용 부품 — 여러 핸들러가 공유하는 인자 파싱·id 검증·응답 매핑.
 * 도구별 로직은 각 핸들러(SearchItemsHandler 등)에, 공통 뼈대는 여기에 둔다.
 */
@Component
class ChatV2ToolSupport(
    @PublishedApi internal val objectMapper: ObjectMapper,
) {
    /** 스키마에 없는 인자는 [tools.jackson.databind.exc.UnrecognizedPropertyException]으로 실패시킨다 — 호출부가 error 결과로 변환. */
    final inline fun <reified T> readArgs(json: String): T =
        objectMapper
            .readerFor(T::class.java)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .readValue(json)

    /**
     * 모델이 지어낸 id 차단 — 이번 턴 검색 결과에 없던 id는 실행 전에 거부한다.
     * (프롬프트 규칙만으로는 검색을 건너뛰고 id를 찍는 사례가 실제로 반복 발생)
     */
    fun validateKnown(
        idRegistry: ChatV2IdRegistry,
        type: ChatV2EntityType,
        id: Long?,
        field: String,
    ): String? {
        if (id == null || idRegistry.isKnown(type, id)) return null
        return errorResult(
            "$field=$id 는 이번 턴에서 검색으로 확인되지 않은 id입니다. " +
                "search_items/search_users로 먼저 검색해 결과의 id만 사용하세요.",
        )
    }

    fun errorResult(message: String): String = objectMapper.writeValueAsString(mapOf("error" to message))

    /**
     * 부모 필터 이름 → id 해소. 모델이 id를 지어내는 대신 이름만 넘기면 서버가 결정적으로 해소한다.
     * (지어낼 id가 없으니 환각 불가 + 병렬 콜도 각자 완결 — [ItemNameMatcher] 재사용)
     */
    fun resolveByName(
        name: String?,
        label: String,
        registryType: ChatV2EntityType,
        idRegistry: ChatV2IdRegistry,
        candidates: () -> List<Pair<Long, String>>,
    ): NameResolution {
        val trimmed = name?.trim()?.takeIf { it.isNotBlank() } ?: return NameResolution.NotRequested
        val matched = candidates().filter { ItemNameMatcher.matches(trimmed, it.second) }
        return when (matched.size) {
            0 ->
                NameResolution.Error(
                    "'$trimmed' $label 을(를) 찾을 수 없습니다. 이름을 변형(음차↔영문, 핵심 단어만)해 다시 조회하거나, " +
                        "없으면 사용자에게 알리세요.",
                )
            1 -> NameResolution.Resolved(matched.first().first).also { idRegistry.register(registryType, it.id) }
            else ->
                NameResolution.Error(
                    "'$trimmed'에 해당하는 $label 이(가) 여러 개입니다: " +
                        matched.take(5).joinToString(", ") { it.second } +
                        ". 사용자에게 어느 것인지 물어보세요.",
                )
        }
    }

    // concise — 훑기·식별·그룹핑용. 부모(epic/project)까지 포함(identity). 무거운 필드·자식 트리는 단건 좁힘 시 자동 부착.
    fun taskMap(task: TaskResponse): Map<String, Any?> =
        mapOf(
            "id" to task.id,
            "name" to task.name,
            "status" to task.status.name,
            "progress" to task.progress,
            "due_date" to task.dueDate?.toString(),
            "assignee" to task.assigneeName,
            // 부모 맥락(에픽·프로젝트) — "업무 그룹별로/프로젝트별로" 그룹핑·필터에 필수(정체성). 이게 없으면
            // 모델이 계층별로 필터를 쪼개 부르며 이름을 추측한다. 부모 이름은 detail이 아니라 identity라 concise에 둔다.
            "epic" to task.epicName,
            "project" to task.projectName,
        )

    fun epicMap(epic: EpicResponse): Map<String, Any?> =
        mapOf(
            "id" to epic.id,
            "name" to epic.name,
            "status" to epic.status.name,
            "due_date" to epic.dueDate?.toString(),
            // 부모 프로젝트 — "프로젝트별 에픽" 그룹핑·맥락(identity). task와 같은 이유로 concise에 둔다.
            "project" to epic.projectName,
        )

    fun projectMap(project: ProjectResponse): Map<String, Any?> =
        mapOf(
            "id" to project.id,
            "name" to project.name,
            "status" to project.status.name,
            "due_date" to project.dueDate?.toString(),
            "pm" to project.pmName,
        )

    fun teamMap(team: TeamResponse): Map<String, Any?> =
        mapOf(
            "id" to team.id,
            "name" to team.name,
            "leader" to team.leaderName,
            "members" to team.members.map { it.name },
        )

    // ── 상세 매핑 (단건 좁힘 시 자동 부착) ──
    // concise map + 무거운 필드(설명·구성원 등). search 결과 DTO가 이미 다 들고 있어 재조회 없음(get_item_details 대체).

    fun taskDetailMap(task: TaskResponse): Map<String, Any?> =
        taskMap(task) + // epic·project는 taskMap에 이미 포함 (identity). detail은 무거운 것만.
            mapOf(
                "description" to task.description,
                "start_date" to task.startDate?.toString(),
            )

    fun epicDetailMap(epic: EpicResponse): Map<String, Any?> =
        epicMap(epic) + // project는 epicMap에 이미 포함
            mapOf(
                "members" to epic.members.map { it.userName },
                "description" to epic.description,
                "start_date" to epic.startDate?.toString(),
            )

    fun projectDetailMap(project: ProjectResponse): Map<String, Any?> =
        projectMap(project) +
            mapOf(
                "description" to project.description,
                "start_date" to project.startDate?.toString(),
                "progress" to project.progress,
                "members" to project.members.map { it.userName },
            )
}
