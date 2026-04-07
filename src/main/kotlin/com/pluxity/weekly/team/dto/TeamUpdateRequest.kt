package com.pluxity.weekly.team.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "팀 수정 요청 (null인 필드는 변경하지 않음)")
data class TeamUpdateRequest(
    @field:Schema(description = "팀명", example = "개발팀")
    @field:Size(max = 255, message = "팀명은 최대 255자까지 입력 가능합니다")
    val name: String? = null,
    @field:Schema(description = "팀장 사용자 ID", example = "1")
    val leaderId: Long? = null,
)
