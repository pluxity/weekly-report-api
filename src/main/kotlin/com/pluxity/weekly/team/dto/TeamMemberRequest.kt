package com.pluxity.weekly.team.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "팀원 추가 요청")
data class TeamMemberRequest(
    @field:Schema(description = "사용자 ID", example = "1", required = true)
    @field:NotNull(message = "사용자 ID는 필수입니다")
    val userId: Long,
)
