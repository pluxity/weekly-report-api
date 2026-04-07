package com.pluxity.weekly.team.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "팀 등록/수정 요청")
data class TeamRequest(
    @field:Schema(description = "팀명 (최대 255자)", example = "개발팀", required = true, maxLength = 255)
    @field:NotBlank(message = "팀명은 필수입니다")
    @field:Size(max = 255, message = "팀명은 최대 255자까지 입력 가능합니다")
    val name: String,
    @field:Schema(description = "팀장 사용자 ID", example = "1")
    val leaderId: Long? = null,
)
