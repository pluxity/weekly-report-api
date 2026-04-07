package com.pluxity.weekly.project.dto

import com.pluxity.weekly.project.entity.ProjectStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "프로젝트 등록/수정 요청")
data class ProjectRequest(
    @field:Schema(description = "프로젝트명 (최대 255자)", example = "2026 1분기 프로젝트", required = true, maxLength = 255)
    @field:NotBlank(message = "프로젝트명은 필수입니다")
    @field:Size(max = 255, message = "프로젝트명은 최대 255자까지 입력 가능합니다")
    val name: String,
    @field:Schema(description = "설명 (최대 1000자)", example = "프로젝트 설명입니다")
    @field:Size(max = 1000, message = "설명은 최대 1000자까지 입력 가능합니다")
    val description: String? = null,
    @field:Schema(description = "상태", example = "TODO")
    val status: ProjectStatus = ProjectStatus.TODO,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: LocalDate? = null,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: LocalDate? = null,
    @field:Schema(description = "담당 PM 사용자 ID", example = "1")
    val pmId: Long? = null,
)
