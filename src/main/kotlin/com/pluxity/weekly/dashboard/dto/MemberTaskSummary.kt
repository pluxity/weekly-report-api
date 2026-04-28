package com.pluxity.weekly.dashboard.dto

import com.pluxity.weekly.task.entity.TaskStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "팀원 태스크 요약")
data class MemberTaskSummary(
    @field:Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    @field:Schema(description = "사용자명", example = "홍길동")
    val userName: String,
    @field:Schema(description = "소속 팀명", example = "백엔드팀")
    val departments: String,
    @field:Schema(description = "태스크 목록")
    val activeTasks: List<MemberTaskBar>,
)

@Schema(description = "팀원 태스크 항목")
data class MemberTaskBar(
    @field:Schema(description = "태스크 ID", example = "1")
    val taskId: Long,
    @field:Schema(description = "태스크명", example = "DB 설계")
    val taskName: String,
    @field:Schema(description = "업무 그룹명", example = "백엔드 구축")
    val epicName: String,
    @field:Schema(description = "프로젝트명", example = "알파 프로젝트")
    val projectName: String,
    @field:Schema(description = "시작일", example = "2026-01-15")
    val startDate: LocalDate?,
    @field:Schema(description = "마감일", example = "2026-02-15")
    val dueDate: LocalDate?,
    @field:Schema(description = "태스크 상태", example = "IN_PROGRESS")
    val status: TaskStatus,
    @field:Schema(description = "진행률 (0~100)", example = "50")
    val progress: Int,
    @field:Schema(description = "일수 차이 (양수=지연중)")
    val daysDelta: Int?,
    @field:Schema(description = "검토 요청일 (REVIEW_REQUEST 상태 기준)")
    val requestDate: LocalDateTime,
)
