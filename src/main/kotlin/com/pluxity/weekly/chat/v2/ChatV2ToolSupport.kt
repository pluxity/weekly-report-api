package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.epic.dto.EpicResponse
import com.pluxity.weekly.project.dto.ProjectResponse
import com.pluxity.weekly.task.dto.TaskResponse
import org.springframework.stereotype.Component
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper

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

    fun errorResult(message: String): String = objectMapper.writeValueAsString(mapOf("error" to message))

    fun taskMap(task: TaskResponse): Map<String, Any?> =
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

    fun epicMap(epic: EpicResponse): Map<String, Any?> =
        mapOf(
            "id" to epic.id,
            "name" to epic.name,
            "project" to epic.projectName,
            "status" to epic.status.name,
            "due_date" to epic.dueDate?.toString(),
            "members" to epic.members.map { it.userName },
        )

    fun projectMap(project: ProjectResponse): Map<String, Any?> =
        mapOf(
            "id" to project.id,
            "name" to project.name,
            "status" to project.status.name,
            "due_date" to project.dueDate?.toString(),
            "pm" to project.pmName,
        )
}
