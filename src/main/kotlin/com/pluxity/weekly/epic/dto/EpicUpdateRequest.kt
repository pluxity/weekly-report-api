package com.pluxity.weekly.epic.dto

import com.pluxity.weekly.epic.entity.EpicStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "업무 그룹 수정 요청 (null인 필드는 변경하지 않음)")
data class EpicUpdateRequest(
    @field:Schema(description = "업무 그룹명", example = "사용자 인증 모듈")
    @field:Size(max = 255, message = "업무 그룹명은 최대 255자까지 입력 가능합니다")
    val name: String? = null,
    @field:Schema(description = "설명")
    @field:Size(max = 1000, message = "설명은 최대 1000자까지 입력 가능합니다")
    val description: String? = null,
    @field:Schema(description = "상태", example = "TODO")
    val status: EpicStatus? = null,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate? = null,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate? = null,
    @field:Schema(description = "배정 사용자 ID 목록", example = "[1, 2]")
    val userIds: List<Long>? = null,
)
